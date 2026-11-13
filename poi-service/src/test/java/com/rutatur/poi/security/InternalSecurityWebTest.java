package com.rutatur.poi.security;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.security.InternalHeaders;
import com.rutatur.poi.controller.InternalPoiController;
import com.rutatur.poi.service.PoiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPoiController.class)
@TestPropertySource(properties = "rutatur.internal-token=secret-test-token")
class InternalSecurityWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PoiService poiService;

    @Test
    void internalEndpointRejectsRequestsWithoutInternalToken() throws Exception {
        mockMvc.perform(get("/internal/pois/search")
                        .param("lat", "-31.4167")
                        .param("lon", "-64.1836"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalEndpointAllowsRequestsWithValidInternalToken() throws Exception {
        when(poiService.searchPois(anyDouble(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(List.of(new PoiDto("node-1", "Plaza", "park", new GeoPointDto(-31.4167, -64.1836), "Centro", 45)));

        mockMvc.perform(get("/internal/pois/search")
                        .header(InternalHeaders.X_INTERNAL_TOKEN, "secret-test-token")
                        .param("lat", "-31.4167")
                        .param("lon", "-64.1836"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("node-1"));
    }
}
