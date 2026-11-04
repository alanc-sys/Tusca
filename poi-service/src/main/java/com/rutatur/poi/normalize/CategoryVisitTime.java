package com.rutatur.poi.normalize;

import com.rutatur.common.enums.Category;
import java.util.Map;

/** Default visit duration per category, in minutes. Always at least one minute. */
public final class CategoryVisitTime {

    private static final Map<Category, Integer> DEFAULTS = Map.of(
            Category.MUSEUM, 90,
            Category.PARK, 45,
            Category.CAFE, 30,
            Category.VIEWPOINT, 20,
            Category.RESTAURANT, 60,
            Category.MONUMENT, 30,
            Category.GALLERY, 45,
            Category.THEATRE, 90,
            Category.CHURCH, 30,
            Category.SHOPPING, 45);

    private CategoryVisitTime() {
    }

    public static int minutesFor(Category category) {
        return Math.max(1, DEFAULTS.getOrDefault(category, 30));
    }
}
