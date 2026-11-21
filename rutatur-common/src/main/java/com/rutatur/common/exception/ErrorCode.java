package com.rutatur.common.exception;

/**
 * Códigos de error de plataforma desacoplados del stack web de Spring (usa int HTTP primitivo).
 */
public enum ErrorCode {

    VALIDATION_ERROR(400, "Invalid request parameters"),
    INVALID_STATE_TRANSITION(400, "Invalid state transition"),
    UNAUTHORIZED(401, "Authentication is required"),
    FORBIDDEN(403, "Access denied"),
    CONSENT_REQUIRED(403, "Mutual consent is required to access contact details"),
    RESOURCE_NOT_FOUND(404, "Requested resource was not found"),
    USER_PROFILE_NOT_FOUND(404, "User profile was not found"),
    DUPLICATE_RESOURCE(409, "Resource already exists"),
    MATCH_NOT_ACCEPTED(409, "Match is not accepted"),
    RATING_ALREADY_EXISTS(409, "Match has already been rated by this user"),
    USER_BLOCKED(403, "User account is blocked"),
    NOT_MATCH_PARTICIPANT(403, "User is not a participant of this match"),
    BUSINESS_RULE_VIOLATION(422, "Business rule violation"),
    INSUFFICIENT_TIME(422, "Available time is insufficient for the requested itinerary"),
    METHOD_NOT_ALLOWED(405, "HTTP method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    INTERNAL_ERROR(500, "Internal server error"),
    EXTERNAL_SERVICE_UNAVAILABLE(503, "External service is currently unavailable");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
