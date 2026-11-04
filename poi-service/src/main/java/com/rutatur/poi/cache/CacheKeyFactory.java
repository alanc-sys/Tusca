package com.rutatur.poi.cache;

import com.rutatur.common.enums.Category;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** Stable cache key: {@code sha256(lat|lng|radius|sorted(categories))} as lowercase hex. */
@Component
public class CacheKeyFactory {

    public String key(double lat, double lng, int radius, List<Category> categories) {
        String sortedCategories = categories == null
                ? ""
                : categories.stream()
                        .distinct()
                        .sorted(Comparator.comparing(Enum::name))
                        .map(Enum::name)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
        String raw = lat + "|" + lng + "|" + radius + "|" + sortedCategories;
        return sha256(raw);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the platform", ex);
        }
    }
}
