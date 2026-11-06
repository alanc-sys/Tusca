package com.rutatur.itinerary.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.InternalItineraryDto;
import com.rutatur.common.dto.InternalItinerarySummary;
import com.rutatur.common.dto.ItineraryRequestDto;
import com.rutatur.common.dto.ItineraryResponseDto;
import com.rutatur.common.dto.SavedItineraryDetail;
import com.rutatur.common.dto.SavedItineraryResponse;
import com.rutatur.common.dto.SavedItinerarySummary;
import com.rutatur.common.enums.TransportMode;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ConflictException;
import com.rutatur.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persist-then-claim flow plus owner-scoped history. Draft TTL is 24 hours, evaluated on read;
 * expired drafts remain rows but are invisible.
 */
@Service
public class ItineraryPersistenceService {

    public static final int DRAFT_TTL_HOURS = 24;
    private static final int ADMIN_PAGE_SIZE = 100;

    private final ItineraryDraftRepository draftRepository;
    private final SavedItineraryRepository savedRepository;
    private final ObjectMapper objectMapper;

    public ItineraryPersistenceService(
            ItineraryDraftRepository draftRepository,
            SavedItineraryRepository savedRepository,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.savedRepository = savedRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID saveDraft(ItineraryRequestDto request, ItineraryResponseDto response) {
        UUID id = response.itineraryId();
        draftRepository.save(new ItineraryDraftEntity(
                id, toJson(request), toJson(response), Instant.now()));
        return id;
    }

    @Transactional
    public SavedItineraryResponse claim(UUID itineraryId, String userId) {
        Instant threshold = Instant.now().minus(DRAFT_TTL_HOURS, ChronoUnit.HOURS);
        ItineraryDraftEntity draft = draftRepository
                .findByIdAndCreatedAtAfter(itineraryId, threshold)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found"));

        if (savedRepository.existsByItineraryId(itineraryId)) {
            throw new ConflictException(
                    ErrorCode.ITINERARY_ALREADY_SAVED, "Itinerary was already saved");
        }

        ItineraryRequestDto request = read(draft.getRequestData(), ItineraryRequestDto.class);
        ItineraryResponseDto response = read(draft.getResponseData(), ItineraryResponseDto.class);
        Instant now = Instant.now();

        SavedItineraryEntity entity = new SavedItineraryEntity(
                itineraryId,
                itineraryId,
                userId,
                BigDecimal.valueOf(response.startPoint().latitude()),
                BigDecimal.valueOf(response.startPoint().longitude()),
                request.availableTime(),
                request.transportMode().name(),
                response.totalTime(),
                routeSummary(response),
                draft.getRequestData(),
                draft.getResponseData(),
                now,
                now);
        try {
            savedRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    ErrorCode.ITINERARY_ALREADY_SAVED, "Itinerary was already saved");
        }
        return new SavedItineraryResponse(entity.getId(), userId, entity.getItineraryId(), now);
    }

    @Transactional(readOnly = true)
    public List<SavedItinerarySummary> list(String userId) {
        return savedRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedItineraryDetail get(UUID id, String userId) {
        return toDetail(requireOwned(id, userId));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        savedRepository.delete(requireOwned(id, userId));
    }

    @Transactional(readOnly = true)
    public List<SavedItinerarySummary> adminList(String userId) {
        return savedRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, ADMIN_PAGE_SIZE))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InternalItineraryDto internalDetail(UUID itineraryId) {
        SavedItineraryEntity entity = savedRepository
                .findById(itineraryId)
                .or(() -> savedRepository.findByItineraryId(itineraryId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found"));

        ItineraryRequestDto request = read(entity.getRequestData(), ItineraryRequestDto.class);
        ItineraryResponseDto response = read(entity.getResponseData(), ItineraryResponseDto.class);
        return new InternalItineraryDto(
                entity.getItineraryId(),
                entity.getUserId(),
                new GeoPointDto(entity.getStartLat().doubleValue(), entity.getStartLng().doubleValue()),
                List.copyOf(request.preferences()),
                request.availableTime(),
                TransportMode.valueOf(entity.getTransportMode()),
                response.totalTime(),
                response.totalVisitTime(),
                response.totalTravelTime(),
                response.route().stream().map(stop -> stop.poi().id()).toList(),
                entity.getRouteSummary(),
                entity.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<InternalItinerarySummary> internalSummaries(String ownerUserId) {
        return savedRepository.findByUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(entity -> new InternalItinerarySummary(
                        entity.getItineraryId(),
                        entity.getUserId(),
                        new GeoPointDto(
                                entity.getStartLat().doubleValue(), entity.getStartLng().doubleValue()),
                        entity.getTotalTime(),
                        entity.getRouteSummary(),
                        entity.getCreatedAt()))
                .toList();
    }

    private SavedItineraryEntity requireOwned(UUID id, String userId) {
        return savedRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ITINERARY_NOT_FOUND, "Itinerary not found"));
    }

    private SavedItinerarySummary toSummary(SavedItineraryEntity entity) {
        return new SavedItinerarySummary(
                entity.getId(),
                entity.getItineraryId(),
                new GeoPointDto(entity.getStartLat().doubleValue(), entity.getStartLng().doubleValue()),
                entity.getTotalTime(),
                entity.getRouteSummary(),
                entity.getCreatedAt());
    }

    private SavedItineraryDetail toDetail(SavedItineraryEntity entity) {
        ItineraryResponseDto response = read(entity.getResponseData(), ItineraryResponseDto.class);
        return new SavedItineraryDetail(
                entity.getId(),
                entity.getUserId(),
                entity.getItineraryId(),
                new GeoPointDto(entity.getStartLat().doubleValue(), entity.getStartLng().doubleValue()),
                entity.getAvailableTime(),
                TransportMode.valueOf(entity.getTransportMode()),
                response.totalTime(),
                response.totalVisitTime(),
                response.totalTravelTime(),
                response.startTime(),
                response.endTime(),
                response.route(),
                entity.getCreatedAt());
    }

    private String routeSummary(ItineraryResponseDto response) {
        List<String> names = response.route().stream().map(stop -> stop.poi().name()).toList();
        return names.stream().limit(3).reduce((a, b) -> a + " + " + b).orElse("Sin paradas");
    }

    private JsonNode toJson(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Cannot serialize itinerary snapshot", ex);
        }
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored itinerary snapshot is unreadable", ex);
        }
    }
}
