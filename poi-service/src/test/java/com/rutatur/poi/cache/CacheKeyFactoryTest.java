package com.rutatur.poi.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.rutatur.common.enums.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheKeyFactoryTest {

    private final CacheKeyFactory factory = new CacheKeyFactory();

    @Test
    void keyIsStableAcrossCategoryOrder() {
        String first = factory.key(-34.6, -58.4, 1000, List.of(Category.PARK, Category.MUSEUM));
        String second = factory.key(-34.6, -58.4, 1000, List.of(Category.MUSEUM, Category.PARK));

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void keyDiffersByRadiusAndCoordinate() {
        assertThat(factory.key(-34.6, -58.4, 1000, List.of(Category.PARK)))
                .isNotEqualTo(factory.key(-34.6, -58.4, 1001, List.of(Category.PARK)));
        assertThat(factory.key(-34.6, -58.4, 1000, List.of(Category.PARK)))
                .isNotEqualTo(factory.key(-34.7, -58.4, 1000, List.of(Category.PARK)));
    }
}
