package com.rutatur.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumVocabularyTest {

    @Test
    void categoryVocabularyIsClosed() {
        assertThat(Category.values()).hasSize(10);
        assertThat(Category.valueOf("MUSEUM")).isEqualTo(Category.MUSEUM);
    }

    @Test
    void transportAndStatusVocabularyAreClosed() {
        assertThat(TransportMode.values()).containsExactly(TransportMode.WALKING, TransportMode.DRIVING);
        assertThat(MatchStatus.values())
                .containsExactly(MatchStatus.PENDING, MatchStatus.ACCEPTED, MatchStatus.REJECTED);
    }
}
