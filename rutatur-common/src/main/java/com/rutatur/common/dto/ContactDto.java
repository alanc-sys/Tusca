package com.rutatur.common.dto;

/** Minimal contact data revealed only after mutual acceptance. */
public record ContactDto(String userId, String username, String email) {
}
