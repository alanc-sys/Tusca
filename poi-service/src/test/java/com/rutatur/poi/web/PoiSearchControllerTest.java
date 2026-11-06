package com.rutatur.poi.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.dto.PoiSearchResponse;
import com.rutatur.common.enums.Category;
import com.rutatur.common.error.ErrorCode;
import com.rutatur.common.exception.UpstreamServiceException;
import com.rutatur.poi.service.PoiSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contract of the public search endpoint: validation, empty results and provider failures. */
@WebMvcTest(controllers = PoiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PoiSearchControllerTest {

    private static final String SEARCH = "/api/pois/search";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PoiSearchService searchService;

    @Test
    void returnsPoisAndTotalCountMatchingTheListSize() throws Exception {
        when(searchService.search(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(PoiSearchResponse.of(List.of(museum(), park())));

        mockMvc.perform(get(SEARCH)
                        .param("lat", "-34.6037")
                        .param("lng", "-58.3816")
                        .param("radius", "1000")
                        .param("categories", "MUSEUM,PARK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pois.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.pois[0].id").value("osm:node/1"));
    }

    @Test
    void emptyProviderResultIsOkNotNotFound() throws Exception {
        when(searchService.search(anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(PoiSearchResponse.of(List.of()));

        mockMvc.perform(get(SEARCH)
                        .param("lat", "-34.6037")
                        .param("lng", "-58.3816")
                        .param("radius", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pois.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void radiusBelowTheMinimumIsRejectedWithoutCallingTheService() throws Exception {
        assertRadiusRejected("0");
    }

    @Test
    void radiusAboveTheMaximumIsRejectedWithoutCallingTheService() throws Exception {
        assertRadiusRejected("5001");
    }

    @Test
    void unknownCategoryIsRejectedWithoutCallingTheProvider() throws Exception {
        mockMvc.perform(get(SEARCH)
                        .param("lat", "-34.6037")
                        .param("lng", "-58.3816")
                        .param("radius", "1000")
                        .param("categories", "CASTLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));

        verifyNoInteractions(searchService);
    }

    @Test
    void providerFailureSurfacesAsServiceUnavailable() throws Exception {
        when(searchService.search(anyDouble(), anyDouble(), anyInt(), any()))
                .thenThrow(new UpstreamServiceException(
                        ErrorCode.POI_PROVIDER_UNAVAILABLE, "POI provider is unavailable"));

        mockMvc.perform(get(SEARCH)
                        .param("lat", "-34.6037")
                        .param("lng", "-58.3816")
                        .param("radius", "1000"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.POI_PROVIDER_UNAVAILABLE.name()));
    }

    private void assertRadiusRejected(String radius) throws Exception {
        mockMvc.perform(get(SEARCH)
                        .param("lat", "-34.6037")
                        .param("lng", "-58.3816")
                        .param("radius", radius))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));

        verifyNoInteractions(searchService);
    }

    private static PoiDto museum() {
        return new PoiDto(
                "osm:node/1",
                "Museo de Bellas Artes",
                Category.MUSEUM,
                new GeoPointDto(-34.6037, -58.3816),
                "Av. del Libertador 1473",
                45,
                4.5,
                List.of("tourism"));
    }

    private static PoiDto park() {
        return new PoiDto(
                "osm:node/2",
                "Parque Centenario",
                Category.PARK,
                new GeoPointDto(-34.6061, -58.4352),
                null,
                30,
                4.2,
                List.of());
    }
}
