package com.rutatur.poi.normalize;

import com.rutatur.common.dto.GeoPointDto;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.Category;
import com.rutatur.common.util.GeoUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps raw Overpass elements to the shared POI contract.
 *
 * <p>Elements without a name, without usable coordinates or without a category mapping are dropped.
 * Duplicates collapse by external id. Radius containment is applied after mapping.
 */
@Component
public class PoiNormalizer {

    private static final Map<String, Category> TAG_TO_CATEGORY = tagMapping();

    private static Map<String, Category> tagMapping() {
        Map<String, Category> mapping = new LinkedHashMap<>();
        mapping.put("tourism=museum", Category.MUSEUM);
        mapping.put("tourism=gallery", Category.GALLERY);
        mapping.put("tourism=viewpoint", Category.VIEWPOINT);
        mapping.put("tourism=attraction", Category.MONUMENT);
        mapping.put("leisure=park", Category.PARK);
        mapping.put("leisure=garden", Category.PARK);
        mapping.put("amenity=cafe", Category.CAFE);
        mapping.put("amenity=restaurant", Category.RESTAURANT);
        mapping.put("amenity=theatre", Category.THEATRE);
        mapping.put("amenity=place_of_worship", Category.CHURCH);
        mapping.put("shop=mall", Category.SHOPPING);
        mapping.put("historic=monument", Category.MONUMENT);
        return mapping;
    }

    public List<PoiDto> normalize(
            JsonNode overpassResponse, double queryLat, double queryLng, int radius, List<Category> categories) {

        Map<String, PoiDto> unique = new LinkedHashMap<>();
        JsonNode elements = overpassResponse == null ? null : overpassResponse.path("elements");
        if (elements == null || !elements.isArray()) {
            return List.of();
        }

        for (JsonNode element : elements) {
            PoiDto poi = mapElement(element, categories);
            if (poi == null) {
                continue;
            }
            double distance = GeoUtils.haversineMeters(
                    queryLat, queryLng, poi.location().latitude(), poi.location().longitude());
            if (distance <= radius) {
                unique.putIfAbsent(poi.id(), poi);
            }
        }

        List<PoiDto> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(result);
    }

    private PoiDto mapElement(JsonNode element, List<Category> categories) {
        String type = element.path("type").asText("");
        String externalId = element.path("id").asText("");
        JsonNode tags = element.path("tags");
        if (type.isBlank() || externalId.isBlank() || tags.isMissingNode() || !tags.isObject()) {
            return null;
        }

        String name = tags.path("name").asText("").trim();
        if (name.isEmpty()) {
            return null;
        }

        Double lat = coordinate(element, "lat", "center", "lat");
        Double lon = coordinate(element, "lon", "center", "lon");
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return null;
        }

        Category category = categoryOf(tags);
        if (category == null) {
            return null;
        }
        if (!categories.isEmpty() && !categories.contains(category)) {
            return null;
        }

        return new PoiDto(
                "osm:" + type + "/" + externalId,
                name,
                category,
                new GeoPointDto(lat, lon),
                address(tags),
                CategoryVisitTime.minutesFor(category),
                rating(tags),
                interestingTags(tags));
    }

    private static Double coordinate(JsonNode element, String directField, String centerField, String centerKey) {
        JsonNode direct = element.get(directField);
        if (direct != null && direct.isNumber()) {
            return direct.asDouble();
        }
        JsonNode center = element.get(centerField);
        if (center != null && center.isObject()) {
            JsonNode nested = center.get(centerKey);
            if (nested != null && nested.isNumber()) {
                return nested.asDouble();
            }
        }
        return null;
    }

    private static Category categoryOf(JsonNode tags) {
        for (Map.Entry<String, Category> entry : TAG_TO_CATEGORY.entrySet()) {
            String[] parts = entry.getKey().split("=");
            if (parts[1].equals(tags.path(parts[0]).asText(""))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String address(JsonNode tags) {
        String full = tags.path("addr:full").asText("").trim();
        if (!full.isEmpty()) {
            return full;
        }
        String street = tags.path("addr:street").asText("").trim();
        String number = tags.path("addr:housenumber").asText("").trim();
        if (street.isEmpty() && number.isEmpty()) {
            return null;
        }
        return (street + (number.isEmpty() ? "" : " " + number)).trim();
    }

    private static double rating(JsonNode tags) {
        String raw = tags.path("rating").asText("").trim();
        if (raw.isEmpty()) {
            return 0.0;
        }
        try {
            return Math.max(0.0, Math.min(5.0, Double.parseDouble(raw.replace(",", "."))));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static List<String> interestingTags(JsonNode tags) {
        List<String> values = new ArrayList<>();
        for (String key : List.of("tourism", "leisure", "amenity", "historic", "cuisine", "shop")) {
            String value = tags.path(key).asText("").trim();
            if (!value.isEmpty()) {
                values.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(values);
    }
}
