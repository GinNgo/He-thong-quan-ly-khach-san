package com.hotel.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzySearchMatcherTest {

    @Test
    void normalizesVietnameseAndScoresTypoHighly() {
        assertThat(FuzzySearchMatcher.score("da nag", "Đà Nẵng")).isGreaterThanOrEqualTo(0.8d);
        assertThat(FuzzySearchMatcher.matches("vung tauu", "Vũng Tàu", 0.75d)).isTrue();
    }

    @Test
    void ranksExactPrefixAndContainsAboveFuzzyMatch() {
        assertThat(FuzzySearchMatcher.score("marriott hotel", "Marriott Hotel"))
                .isGreaterThan(FuzzySearchMatcher.score("marriot", "Marriott Hotel"));
        assertThat(FuzzySearchMatcher.score("marriot", "Marriott Hotel"))
                .isGreaterThan(FuzzySearchMatcher.score("xyz", "Marriott Hotel"));
    }

    @Test
    void rejectsVeryShortOrUnrelatedQueries() {
        assertThat(FuzzySearchMatcher.matches("m", "Mỹ Tho", 0.75d)).isFalse();
        assertThat(FuzzySearchMatcher.matches("xyz", "Đà Nẵng", 0.75d)).isFalse();
    }
}
