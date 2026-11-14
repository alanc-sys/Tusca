package com.rutatur.match.dto;

import java.util.UUID;

public record MatchContactResponse(
        UUID matchId,
        String counterpartyUserId,
        String counterpartyUsername,
        String counterpartyEmail,
        String counterpartyItineraryName
) {
}
