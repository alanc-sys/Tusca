package com.rutatur.poi.client;

import com.rutatur.common.enums.Category;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the Overpass QL body for a bounded around-query. */
@Component
public class OverpassQueryBuilder {

    private static final Map<Category, List<String>> CATEGORY_TAGS = Map.of(
            Category.MUSEUM, List.of("tourism=museum"),
            Category.PARK, List.of("leisure=park"),
            Category.CAFE, List.of("amenity=cafe"),
            Category.VIEWPOINT, List.of("tourism=viewpoint"),
            Category.RESTAURANT, List.of("amenity=restaurant"),
            Category.MONUMENT, List.of("historic=monument", "tourism=attraction"),
            Category.GALLERY, List.of("tourism=gallery"),
            Category.THEATRE, List.of("amenity=theatre"),
            Category.CHURCH, List.of("amenity=place_of_worship"),
            Category.SHOPPING, List.of("shop=mall"));

    public String build(double lat, double lng, int radius, List<Category> categories) {
        List<Category> requested = categories == null || categories.isEmpty()
                ? List.copyOf(CATEGORY_TAGS.keySet())
                : categories;

        StringBuilder query = new StringBuilder("[out:json][timeout:25];\n(\n");
        for (Category category : requested) {
            for (String tag : CATEGORY_TAGS.getOrDefault(category, List.of())) {
                String[] parts = tag.split("=");
                for (String elementType : List.of("node", "way", "relation")) {
                    query.append("  ")
                            .append(elementType)
                            .append("[\"")
                            .append(parts[0])
                            .append("\"=\"")
                            .append(parts[1])
                            .append("\"](around:")
                            .append(radius)
                            .append(',')
                            .append(String.format(Locale.ROOT, "%.6f", lat))
                            .append(',')
                            .append(String.format(Locale.ROOT, "%.6f", lng))
                            .append(");\n");
                }
            }
        }
        query.append(");\nout center tags;\n");
        return query.toString();
    }
}
