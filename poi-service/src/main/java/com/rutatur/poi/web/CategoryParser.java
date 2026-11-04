package com.rutatur.poi.web;

import com.rutatur.common.enums.Category;
import com.rutatur.common.exception.InvalidRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses the comma-separated {@code categories} query parameter against the closed vocabulary. */
public final class CategoryParser {

    private CategoryParser() {
    }

    public static List<Category> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Category> categories = new ArrayList<>();
        for (String token : raw.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                Category category = Category.valueOf(value.toUpperCase(Locale.ROOT));
                if (!categories.contains(category)) {
                    categories.add(category);
                }
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("Unknown category: " + value);
            }
        }
        return List.copyOf(categories);
    }
}
