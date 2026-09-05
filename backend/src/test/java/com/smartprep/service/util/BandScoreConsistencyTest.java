package com.smartprep.service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link IeltsScoringUtils} as the single source of truth for band conversion.
 * <p>
 * The codebase previously carried four separate implementations of this arithmetic:
 * a 14-entry table in ReadingGradingService, a near-duplicate 41-entry listening map in
 * ListeningGradingService, the canonical tables here, and a fourth copy of the rounding
 * rule in StatsService. The duplicates had drifted, so the same performance produced
 * different bands depending on which screen the user came from.
 */
class BandScoreConsistencyTest {

    /**
     * The 13-question reading table that used to live in ReadingGradingService jumped
     * 6.5 -> 7.5 -> 8.5, making bands 7.0 and 8.0 literally unreachable in practice mode.
     * They must exist on the shared scale.
     */
    @Test
    @DisplayName("every half band from 1.0 to 9.0 is reachable on the reading scale")
    void readingScale_hasNoUnreachableBands() {
        Set<BigDecimal> reachable = new HashSet<>();
        for (int raw = 0; raw <= 40; raw++) {
            reachable.add(IeltsScoringUtils.calculateReadingBand(raw));
        }
        assertTrue(reachable.contains(new BigDecimal("7.0")),
                "band 7.0 must be reachable on the reading scale");
        assertTrue(reachable.contains(new BigDecimal("8.0")),
                "band 8.0 must be reachable on the reading scale");
    }

    @Test
    @DisplayName("every half band from 1.0 to 9.0 is reachable on the listening scale")
    void listeningScale_hasNoUnreachableBands() {
        Set<BigDecimal> reachable = new HashSet<>();
        for (int raw = 0; raw <= 40; raw++) {
            reachable.add(IeltsScoringUtils.calculateListeningBand(raw));
        }
        assertTrue(reachable.contains(new BigDecimal("7.0")));
        assertTrue(reachable.contains(new BigDecimal("8.0")));
    }

    /**
     * A full 40-question paper must never be rescaled — scaling has to be a no-op there,
     * otherwise mock tests and practice would disagree again by a different route.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 13, 20, 30, 32, 36, 39, 40})
    @DisplayName("scaling a 40-question paper leaves the band unchanged")
    void fortyQuestionPaper_isNotRescaled(int raw) {
        assertEquals(IeltsScoringUtils.calculateListeningBand(raw),
                IeltsScoringUtils.calculateListeningBand(raw, 40));
        assertEquals(IeltsScoringUtils.calculateReadingBand(raw, "ACADEMIC"),
                IeltsScoringUtils.calculateReadingBand(raw, 40, "ACADEMIC"));
    }

    @Test
    @DisplayName("a perfect short paper scores 9.0, the same as a perfect full paper")
    void perfectShortPaper_matchesPerfectFullPaper() {
        assertEquals(IeltsScoringUtils.calculateReadingBand(40, "ACADEMIC"),
                IeltsScoringUtils.calculateReadingBand(13, 13, "ACADEMIC"));
        assertEquals(IeltsScoringUtils.calculateListeningBand(40),
                IeltsScoringUtils.calculateListeningBand(10, 10));
    }

    @Test
    @DisplayName("a blank short paper scores the same as a blank full paper")
    void blankShortPaper_matchesBlankFullPaper() {
        assertEquals(IeltsScoringUtils.calculateReadingBand(0, "ACADEMIC"),
                IeltsScoringUtils.calculateReadingBand(0, 13, "ACADEMIC"));
        // The old ListeningGradingService map returned 0.0 here while the canonical map
        // returns 1.0, so an identical blank paper reported a different band per path.
        assertEquals(new BigDecimal("1.0"), IeltsScoringUtils.calculateListeningBand(0, 10));
    }

    @Test
    @DisplayName("Academic and General Training reading tables genuinely differ")
    void academicAndGeneralTraining_areDistinct() {
        boolean anyDifference = false;
        for (int raw = 0; raw <= 40; raw++) {
            if (!IeltsScoringUtils.calculateReadingBand(raw, "ACADEMIC")
                    .equals(IeltsScoringUtils.calculateReadingBand(raw, "GENERAL_TRAINING"))) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference,
                "GT and Academic must not share one table — MockTestService assumed Academic for both");
    }

    @Test
    @DisplayName("degenerate inputs do not throw")
    void degenerateInputs_areSafe() {
        assertEquals(new BigDecimal("1.0"), IeltsScoringUtils.calculateListeningBand(0, 0));
        assertEquals(IeltsScoringUtils.calculateListeningBand(40),
                IeltsScoringUtils.calculateListeningBand(99, 10));
        assertEquals(new BigDecimal("1.0"), IeltsScoringUtils.calculateListeningBand(-5, 10));
    }
}
