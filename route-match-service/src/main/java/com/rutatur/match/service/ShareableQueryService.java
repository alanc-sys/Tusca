package com.rutatur.match.service;

import com.rutatur.common.dto.InternalItinerarySummary;
import com.rutatur.common.dto.ShareableItineraryDto;
import com.rutatur.match.client.ItineraryInternalClient;
import com.rutatur.match.persistence.SharedRouteEntity;
import com.rutatur.match.persistence.SharedRouteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's itineraries annotated with their share state, so published routes stand out. */
@Service
public class ShareableQueryService {

    private final ItineraryInternalClient itineraryClient;
    private final SharedRouteRepository sharedRouteRepository;
    private final Clock clock;

    public ShareableQueryService(
            ItineraryInternalClient itineraryClient,
            SharedRouteRepository sharedRouteRepository,
            Clock clock) {
        this.itineraryClient = itineraryClient;
        this.sharedRouteRepository = sharedRouteRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShareableItineraryDto> shareable(String userId) {
        List<InternalItinerarySummary> summaries = itineraryClient.summaries(userId);
        Map<UUID, SharedRouteEntity> shares = sharedRouteRepository.findByOwnerUserId(userId).stream()
                .collect(Collectors.toMap(
                        SharedRouteEntity::getItineraryId, Function.identity(), (left, right) -> left));
        Instant now = clock.instant();

        return summaries.stream()
                .map(summary -> {
                    SharedRouteEntity share = shares.get(summary.itineraryId());
                    boolean shared = share != null && share.isUsable(now);
                    return new ShareableItineraryDto(
                            summary.itineraryId(),
                            summary.startPoint(),
                            summary.totalTime(),
                            shared,
                            shared ? share.getId() : null,
                            shared ? share.getExpiresAt() : null);
                })
                .toList();
    }
}
