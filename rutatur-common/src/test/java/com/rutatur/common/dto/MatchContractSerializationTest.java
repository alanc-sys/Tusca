package com.rutatur.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchContractSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void suggestionSerializesWithoutContactOrCoordinates() throws Exception {
        MatchSuggestionDto dto = new MatchSuggestionDto(
                UUID.randomUUID(), UUID.randomUUID(), 0.875, 2, List.of("osm:node/1"), Instant.parse("2026-11-03T12:00:00Z"));

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"targetRouteId\"", "\"matchScore\":0.875", "\"matchedPoiIds\"");
        assertThat(json).doesNotContain("email", "username", "phone", "latitude", "longitude", "userId");
    }

    @Test
    void contactExposesOnlyMinimalFields() throws Exception {
        String json = mapper.writeValueAsString(new ContactDto("user-b", "user2", "user2@rutatur.local"));

        assertThat(json).isEqualTo(
                "{\"userId\":\"user-b\",\"username\":\"user2\",\"email\":\"user2@rutatur.local\"}");
    }
}
