package com.rutatur.poi.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.config.PoiProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OverpassClientTest {

    @Test
    void openCircuitFailsFastWithoutQueryingTheProvider() {
        OverpassQueryBuilder queryBuilder = mock(OverpassQueryBuilder.class);
        when(queryBuilder.build(anyDouble(), anyDouble(), anyInt(), any())).thenReturn("query");
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("overpass").transitionToOpenState();
        OverpassClient client = new OverpassClient(
                RestClient.builder(),
                new PoiProperties(null, null, null, null, null),
                queryBuilder,
                registry);

        assertThatThrownBy(() -> client.fetch(1.0, 2.0, 300, List.of()))
                .isInstanceOf(UpstreamServiceException.class)
                .extracting(ex -> ((UpstreamServiceException) ex).getCode())
                .isEqualTo(ErrorCode.POI_PROVIDER_UNAVAILABLE);
    }
}
