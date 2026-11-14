package com.rutatur.match.client;

import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.common.security.InternalHeaders;
import com.rutatur.match.dto.ItinerarySnapshotDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class ItineraryInternalClient {

    private final RestClient restClient;
    private final String internalToken;

    public ItineraryInternalClient(
            RestClient.Builder builder,
            @Value("${services.itinerary.url}") String itineraryServiceUrl,
            @Value("${rutatur.internal-token}") String internalToken) {
        this.restClient = builder.baseUrl(itineraryServiceUrl).build();
        this.internalToken = internalToken;
    }

    public ItinerarySnapshotDto getItinerary(UUID itineraryId) {
        try {
            ItinerarySnapshotDto snapshot = restClient.get()
                    .uri("/internal/itineraries/{id}", itineraryId)
                    .header(InternalHeaders.X_INTERNAL_TOKEN, internalToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new RutaturException(
                                ErrorCode.RESOURCE_NOT_FOUND,
                                "Itinerario no encontrado: " + itineraryId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RutaturException(
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                "Error consultando itinerary-service: HTTP " + res.getStatusCode().value());
                    })
                    .body(ItinerarySnapshotDto.class);

            if (snapshot == null) {
                throw new RutaturException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Itinerario no encontrado: " + itineraryId);
            }
            return snapshot;
        } catch (RutaturException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new RutaturException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "No se pudo comunicar con itinerary-service: " + ex.getMessage());
        }
    }
}
