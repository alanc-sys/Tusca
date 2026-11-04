package com.rutatur.itinerary.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import org.junit.jupiter.api.Test;

class OsrmTableClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void missingDurationsIsAContractFailure() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"distances": [[0, 10], [10, 0]]}
                """);

        assertThatThrownBy(() -> OsrmTableClient.parseMatrix(body, 2))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void nonArrayDurationsIsAContractFailure() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"durations": "not-a-matrix", "distances": [[0, 10], [10, 0]]}
                """);

        assertThatThrownBy(() -> OsrmTableClient.parseMatrix(body, 2))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void matrixShapeThatDoesNotMatchTheRequestIsAContractFailure() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {
                  "durations": [[0, 10], [10, 0]],
                  "distances": [[0, 10], [10, 0]]
                }
                """);

        assertThatThrownBy(() -> OsrmTableClient.parseMatrix(body, 3))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void rowWithTheWrongColumnCountIsAContractFailure() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {
                  "durations": [[0, 10], [10]],
                  "distances": [[0, 10], [10, 0]]
                }
                """);

        assertThatThrownBy(() -> OsrmTableClient.parseMatrix(body, 2))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void missingDistancesIsAContractFailure() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"durations": [[0, 10], [10, 0]]}
                """);

        assertThatThrownBy(() -> OsrmTableClient.parseMatrix(body, 2))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.ROUTING_PROVIDER_UNAVAILABLE);
    }

    @Test
    void nullCellsBecomeUnreachable() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {
                  "durations": [[0, null], [null, 0]],
                  "distances": [[0, 10], [10, 0]]
                }
                """);

        RoutingMatrix matrix = OsrmTableClient.parseMatrix(body, 2);

        assertThat(matrix.durationMinutes(0, 1)).isEqualTo(RoutingMatrix.UNREACHABLE);
        assertThat(matrix.durationMinutes(1, 0)).isEqualTo(RoutingMatrix.UNREACHABLE);
    }

    @Test
    void secondsAreRoundedHalfUpToMinutesAndMetersToIntegers() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {
                  "durations": [[0, 90], [30, 0]],
                  "distances": [[0, 123.6], [7.4, 0]]
                }
                """);

        RoutingMatrix matrix = OsrmTableClient.parseMatrix(body, 2);

        assertThat(matrix.durationMinutes(0, 1)).isEqualTo(2);
        assertThat(matrix.durationMinutes(1, 0)).isEqualTo(1);
        assertThat(matrix.distanceMeters(0, 1)).isEqualTo(124);
        assertThat(matrix.distanceMeters(1, 0)).isEqualTo(7);
    }
}
