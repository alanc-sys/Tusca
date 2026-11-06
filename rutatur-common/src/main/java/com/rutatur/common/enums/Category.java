package com.rutatur.common.enums;

/**
 * Closed POI vocabulary shared by every RutaTur module.
 *
 * <p>An unknown value fails Jackson conversion, which surfaces as a 400 at the edge.
 */
public enum Category {
    MUSEUM,
    PARK,
    CAFE,
    VIEWPOINT,
    RESTAURANT,
    MONUMENT,
    GALLERY,
    THEATRE,
    CHURCH,
    SHOPPING
}
