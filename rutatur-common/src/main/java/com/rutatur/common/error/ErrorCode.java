package com.rutatur.common.error;

/**
 * Stable error vocabulary. The HTTP status is carried by the code so status and code can never
 * disagree.
 *
 * <p>Kept as an {@code int} so the shared jar stays free of the Spring web stack.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400),
    UNAUTHORIZED(401),
    INTERNAL_AUTH_REQUIRED(401),
    ACCESS_DENIED(403),
    MATCH_ACCESS_DENIED(403),
    CONTACT_NOT_REVEALED(403),
    ITINERARY_NOT_FOUND(404),
    SHARE_NOT_FOUND(404),
    MATCH_NOT_FOUND(404),
    ROUTE_NOT_FOUND(404),
    MATCH_TRANSITION_INVALID(409),
    ITINERARY_ALREADY_SAVED(409),
    ITINERARY_NOT_FEASIBLE(422),
    NO_ACTIVE_SHARE(422),
    POI_PROVIDER_UNAVAILABLE(503),
    ROUTING_PROVIDER_UNAVAILABLE(503),
    CONTACT_PROVIDER_UNAVAILABLE(503),
    UPSTREAM_UNAVAILABLE(503),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int statusValue() {
        return httpStatus;
    }
}
