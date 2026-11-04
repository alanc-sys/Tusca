package com.rutatur.itinerary.optimize;

import com.rutatur.common.dto.PoiDto;
import com.rutatur.common.enums.Category;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Preference-first score: {@code 0.7 * preferenceScore + 0.3 * ratingScore}.
 *
 * <p>{@code preferenceScore = (P - i) / P} where {@code i} is the zero-based index of the POI's
 * category in the preference list; an unlisted category scores 0.
 */
@Component
public class CandidateScorer {

    public double score(PoiDto poi, List<Category> preferences) {
        int index = preferences.indexOf(poi.category());
        double preferenceScore = index < 0 ? 0.0 : (preferences.size() - index) / (double) preferences.size();
        double ratingScore = Math.max(0.0, Math.min(5.0, poi.rating())) / 5.0;
        return 0.7 * preferenceScore + 0.3 * ratingScore;
    }

    /** Total order: score desc, rating desc, visit time asc, id asc. */
    public Comparator<PoiDto> comparator(List<Category> preferences) {
        Comparator<PoiDto> byScore =
                Comparator.comparingDouble((PoiDto poi) -> score(poi, preferences)).reversed();
        Comparator<PoiDto> byRating = Comparator.comparingDouble(PoiDto::rating).reversed();
        return byScore.thenComparing(byRating)
                .thenComparingInt(PoiDto::visitTime)
                .thenComparing(PoiDto::id);
    }
}
