package com.rutatur.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.InternalItineraryDto;
import com.rutatur.common.dto.SharedRouteDto;
import com.rutatur.common.enums.Category;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.client.ItineraryInternalClient;
import com.rutatur.match.config.MatchProperties;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;

class ShareServiceTest {

    private static final String OWNER = "owner-sub";
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MatchProperties PROPERTIES = new MatchProperties(
            null, null, null, null, null, Duration.ofDays(7), null, null, null);

    private final ItineraryInternalClient itineraryClient = mock(ItineraryInternalClient.class);
    private final SharedRouteRepository repository = mock(SharedRouteRepository.class);
    private final ShareService service = new ShareService(itineraryClient, repository, PROPERTIES, CLOCK);

    private UUID itineraryId;

    @BeforeEach
    void setUp() {
        itineraryId = UUID.randomUUID();
        when(itineraryClient.detail(itineraryId)).thenReturn(detail(OWNER));
        when(repository.findByItineraryId(itineraryId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sharePersistsSnapshotAndCreatesActiveRoute() {
        ShareService.ShareResult result = service.share(itineraryId, OWNER);

        assertThat(result.created()).isTrue();
        SharedRouteDto dto = result.dto();
        assertThat(dto.itineraryId()).isEqualTo(itineraryId);
        assertThat(dto.active()).isTrue();
        assertThat(dto.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));

        SharedRouteEntity saved = captured();
        assertThat(saved.getOwnerUserId()).isEqualTo(OWNER);
        assertThat(saved.getPoiIds()).containsExactly("p1", "p2");
        assertThat(saved.getCategories()).containsExactly(Category.PARK.name());
        assertThat(saved.getAvailableTime()).isEqualTo(120);
        assertThat(saved.getStartLat()).isEqualByComparingTo(BigDecimal.valueOf(-34.6037));
    }

    @Test
    void foreignItineraryIsNotFoundAndWritesNothing() {
        when(itineraryClient.detail(itineraryId)).thenReturn(detail("someone-else"));

        assertThatThrownBy(() -> service.share(itineraryId, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownItineraryPropagatesNotFound() {
        when(itineraryClient.detail(itineraryId)).thenThrow(
                new ResourceNotFoundException(ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found"));

        assertThatThrownBy(() -> service.share(itineraryId, OWNER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        verify(repository, never()).save(any());
    }

    @Test
    void repeatedShareReturnsExistingActiveShare() {
        SharedRouteEntity existing = share(true, NOW.plusSeconds(3600));
        when(repository.findByItineraryId(itineraryId)).thenReturn(Optional.of(existing));

        ShareService.ShareResult result = service.share(itineraryId, OWNER);

        assertThat(result.created()).isFalse();
        assertThat(result.dto().id()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void expiredShareIsReactivatedWithFreshExpiry() {
        SharedRouteEntity expired = share(false, NOW.minusSeconds(3600));
        when(repository.findByItineraryId(itineraryId)).thenReturn(Optional.of(expired));

        ShareService.ShareResult result = service.share(itineraryId, OWNER);

        assertThat(result.created()).isFalse();
        assertThat(result.dto().active()).isTrue();
        assertThat(result.dto().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        verify(repository).save(expired);
    }

    @Test
    void concurrentShareAbsorbsTheUniqueConstraintRace() {
        SharedRouteEntity winner = share(true, NOW.plusSeconds(3600));
        when(repository.findByItineraryId(itineraryId))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        ShareService.ShareResult result = service.share(itineraryId, OWNER);

        assertThat(result.created()).isFalse();
        assertThat(result.dto().id()).isEqualTo(winner.getId());
    }

    @Test
    void itineraryServiceFailureLeavesNoShare() {
        when(itineraryClient.detail(itineraryId)).thenThrow(
                new UpstreamServiceException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.share(itineraryId, OWNER))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        verify(repository, never()).save(any());
    }

    private SharedRouteEntity captured() {
        org.mockito.ArgumentCaptor<SharedRouteEntity> captor =
                org.mockito.ArgumentCaptor.forClass(SharedRouteEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private SharedRouteEntity share(boolean active, Instant expiresAt) {
        return new SharedRouteEntity(
                UUID.randomUUID(),
                itineraryId,
                OWNER,
                BigDecimal.valueOf(-34.6037),
                BigDecimal.valueOf(-58.3816),
                120,
                "WALKING",
                90,
                List.of("p1"),
                List.of(Category.PARK.name()),
                active,
                NOW.minusSeconds(7200),
                expiresAt);
    }

    private InternalItineraryDto detail(String owner) {
        return new InternalItineraryDto(
                itineraryId,
                owner,
                new GeoPointDto(-34.6037, -58.3816),
                List.of(Category.PARK),
                120,
                TransportMode.WALKING,
                90,
                60,
                30,
                List.of("p1", "p2"),
                "A + B",
                NOW.minusSeconds(600));
    }
}
