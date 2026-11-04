package com.rutatur.match.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.ResourceNotFoundException;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.match.config.MatchProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ItineraryInternalClientTest {

    private static final MatchProperties PROPERTIES = new MatchProperties(
            "http://itinerary.test:8082",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    @Test
    void upstreamNotFoundStaysANotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://itinerary.test:8082");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ItineraryInternalClient client = new ItineraryInternalClient(builder.build(), "token");
        UUID itineraryId = UUID.randomUUID();
        server.expect(requestTo("http://itinerary.test:8082/internal/itineraries/" + itineraryId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.detail(itineraryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(ex -> ((ResourceNotFoundException) ex).getCode())
                .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
        server.verify();
    }

    @Test
    void undecodableBodyBecomesUpstreamUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://itinerary.test:8082");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ItineraryInternalClient client = new ItineraryInternalClient(builder.build(), "token");
        UUID itineraryId = UUID.randomUUID();
        server.expect(requestTo("http://itinerary.test:8082/internal/itineraries/" + itineraryId))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.detail(itineraryId))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
        server.verify();
    }
}
