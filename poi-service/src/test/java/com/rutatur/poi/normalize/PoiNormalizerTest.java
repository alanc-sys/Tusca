package com.rutatur.poi.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoiNormalizerTest {

    private final PoiNormalizer normalizer = new PoiNormalizer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dropsIncompleteAndUnmappableElements() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"elements":[
                  {"type":"node","id":1,"lat":-34.60,"lon":-58.38,"tags":{"tourism":"museum"}},
                  {"type":"node","id":2,"lat":-34.60,"lon":-58.38,"tags":{"name":"Nameless"}},
                  {"type":"node","id":3,"tags":{"name":"No coords","tourism":"museum"}},
                  {"type":"node","id":4,"lat":-34.60,"lon":-58.38,"tags":{"name":"Shop","shop":"bakery"}},
                  {"type":"node","id":5,"lat":-34.60,"lon":-58.38,
                   "tags":{"name":"Bellas Artes","tourism":"museum","addr:street":"Av. Libertador","addr:housenumber":"1473"}}
                ]}
                """);

        List<PoiDto> pois = normalizer.normalize(payload, -34.60, -58.38, 1000, List.of());

        assertThat(pois).hasSize(1);
        PoiDto poi = pois.get(0);
        assertThat(poi.id()).isEqualTo("osm:node/5");
        assertThat(poi.name()).isEqualTo("Bellas Artes");
        assertThat(poi.category()).isEqualTo(Category.MUSEUM);
        assertThat(poi.visitTime()).isEqualTo(90);
        assertThat(poi.address()).isEqualTo("Av. Libertador 1473");
    }

    @Test
    void collapsesDuplicatesAndAppliesRadius() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"elements":[
                  {"type":"node","id":10,"lat":-34.6000,"lon":-58.3800,"tags":{"name":"Park","leisure":"park"}},
                  {"type":"node","id":10,"lat":-34.6000,"lon":-58.3800,"tags":{"name":"Park again","leisure":"park"}},
                  {"type":"node","id":11,"lat":-34.7000,"lon":-58.3800,"tags":{"name":"Far","leisure":"park"}}
                ]}
                """);

        List<PoiDto> pois = normalizer.normalize(payload, -34.60, -58.38, 1000, List.of(Category.PARK));

        assertThat(pois).extracting(PoiDto::id).containsExactly("osm:node/10");
        assertThat(pois.get(0).visitTime()).isEqualTo(45);
    }

    @Test
    void categoryFilterExcludesOtherCategories() throws Exception {
        JsonNode payload = mapper.readTree("""
                {"elements":[
                  {"type":"node","id":20,"lat":-34.60,"lon":-58.38,"tags":{"name":"Museum","tourism":"museum"}},
                  {"type":"node","id":21,"lat":-34.60,"lon":-58.38,"tags":{"name":"Cafe","amenity":"cafe"}}
                ]}
                """);

        List<PoiDto> pois = normalizer.normalize(payload, -34.60, -58.38, 1000, List.of(Category.CAFE));

        assertThat(pois).extracting(PoiDto::category).containsExactly(Category.CAFE);
    }
}
