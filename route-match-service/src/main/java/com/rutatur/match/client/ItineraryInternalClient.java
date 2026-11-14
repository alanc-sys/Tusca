package com.rutatur.match.client;

import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.common.security.InternalHeaders;
import com.rutatur.match.dto.ItinerarySnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class ItineraryInternalClient {

    private static final Logger log = LoggerFactory.getLogger(ItineraryInternalClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public ItineraryInternalClient(
            RestClient.Builder builder,
            @Value("${services.itinerary.url}") String itineraryServiceUrl,
            @Value("${rutatur.internal-token}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(10000);

        this.restClient = builder
                .requestFactory(factory)
                .baseUrl(itineraryServiceUrl)
                .build();
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
                        log.error("Error consultando itinerary-service para {}: HTTP {}", itineraryId, res.getStatusCode().value());
                        throw new RutaturException(
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                "Servicio de itinerarios temporalmente no disponible");
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
            log.error("Fallo de red comunicando con itinerary-service para {}: {}", itineraryId, ex.getMessage());
            throw new RutaturException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Servicio de itinerarios temporalmente no disponible");
        }
    }
}
