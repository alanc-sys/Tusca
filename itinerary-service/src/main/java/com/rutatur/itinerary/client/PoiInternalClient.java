package com.rutatur.itinerary.client;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.exception.ErrorCode;
import com.rutatur.common.exception.RutaturException;
import com.rutatur.common.security.InternalHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Cliente HTTP interno hacia poi-service (/internal/pois/search) usando X-Internal-Token.
 */
@Component
public class PoiInternalClient {

    private final RestClient restClient;
    private final String poiServiceUrl;
    private final String internalToken;

    public PoiInternalClient(
            @Value("${services.poi.url:http://poi-service:8081}") String poiServiceUrl,
            @Value("${rutatur.internal-token}") String internalToken
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(10000);

        this.poiServiceUrl = poiServiceUrl;
        this.internalToken = internalToken;
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<PoiDto> searchCandidatePois(double lat, double lon, int radiusMeters, List<String> categories) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(poiServiceUrl + "/internal/pois/search")
                .queryParam("lat", lat)
                .queryParam("lon", lon)
                .queryParam("radiusMeters", radiusMeters);

        if (categories != null && !categories.isEmpty()) {
            uriBuilder.queryParam("categories", String.join(",", categories));
        }

        try {
            List<PoiDto> result = restClient.get()
                    .uri(uriBuilder.toUriString())
                    .header(InternalHeaders.X_INTERNAL_TOKEN, internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return result == null ? List.of() : result;
        } catch (Exception ex) {
            throw new RutaturException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "No se pudo consultar poi-service internamente: " + ex.getMessage()
            );
        }
    }
}
