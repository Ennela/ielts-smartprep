package com.smartprep.service.util;

import com.smartprep.model.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IeltsScoringUtils}.
 * Covers:
 *   - Listening raw score → band mapping
 *   - Reading raw score → band mapping
 *   - Listening answer matching (MCQ, FILL_BLANK, spelling normalization)
 *   - Reading answer matching (all QuestionTypes)
 *   - Overall band rounding (IELTS rules)
 */
class IeltsScoringUtilsTest {

    // ===================================================================
    //  Listening Band Mapping
    // ===================================================================

    @Nested
    @DisplayName("calculateListeningBand")
    class ListeningBand {

        @ParameterizedTest(name = "raw {0} → band {1}")
        @CsvSource({
            "40, 9.0",
            "39, 9.0",
            "38, 8.5",
            "35, 8.0",
            "34, 7.5",
            "30, 7.0",
            "27, 6.5",
            "23, 6.0",
            "20, 5.5",
            "16, 5.0",
            "13, 4.5",
            "10, 4.0",
            "7,  3.5",
            "4,  3.0",
            "3,  2.5",
            "2,  2.0",
            "1,  1.0",
            "0,  1.0"
        })
        void shouldMapRawScoreToBand(int raw, String expectedBand) {
            assertThat(IeltsScoringUtils.calculateListeningBand(raw))
                    .isEqualByComparingTo(new BigDecimal(expectedBand));
        }

        @Test
        @DisplayName("negative score clamped to 0 → band 1.0")
        void negativeClampsToZero() {
            assertThat(IeltsScoringUtils.calculateListeningBand(-5))
                    .isEqualByComparingTo(new BigDecimal("1.0"));
        }

        @Test
        @DisplayName("score > 40 clamped to 40 → band 9.0")
        void overMaxClampsTo40() {
            assertThat(IeltsScoringUtils.calculateListeningBand(45))
                    .isEqualByComparingTo(new BigDecimal("9.0"));
        }
    }

    // ===================================================================
    //  Reading Band Mapping
    // ===================================================================

    @Nested
    @DisplayName("calculateReadingBand")
    class ReadingBand {

        @ParameterizedTest(name = "raw {0} → band {1}")
        @CsvSource({
            "40, 9.0",
            "39, 9.0",
            "38, 8.5",
            "35, 8.0",
            "33, 7.5",
            "30, 7.0",
            "27, 6.5",
            "24, 6.0",
            "20, 5.5",
            "16, 5.0",
            "13, 4.5",
            "10, 4.0",
            "8,  3.5",
            "6,  3.0",
            "4,  2.5",
            "2,  2.0",
            "1,  1.0",
            "0,  1.0"
        })
        void shouldMapRawScoreToBand(int raw, String expectedBand) {
            assertThat(IeltsScoringUtils.calculateReadingBand(raw))
                    .isEqualByComparingTo(new BigDecimal(expectedBand));
        }

        @Test
        @DisplayName("negative score clamped to 0 → band 1.0")
        void negativeClampsToZero() {
            assertThat(IeltsScoringUtils.calculateReadingBand(-3))
                    .isEqualByComparingTo(new BigDecimal("1.0"));
        }

        @Test
        @DisplayName("score > 40 clamped to 40 → band 9.0")
        void overMaxClampsTo40() {
            assertThat(IeltsScoringUtils.calculateReadingBand(50))
                    .isEqualByComparingTo(new BigDecimal("9.0"));
        }
    }

    // ===================================================================
    //  Listening Answer Matching
    // ===================================================================

    @Nested
    @DisplayName("isListeningCorrect")
    class ListeningCorrect {

        @Test
        @DisplayName("exact match (MCQ) — case insensitive")
        void mcq_exactMatch() {
            assertThat(IeltsScoringUtils.isListeningCorrect("B", "b", "MCQ")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("A", "B", "MCQ")).isFalse();
        }

        @Test
        @DisplayName("FILL_BLANK — exact match")
        void fillBlank_exactMatch() {
            assertThat(IeltsScoringUtils.isListeningCorrect("library", "Library", "FILL_BLANK")).isTrue();
        }

        @Test
        @DisplayName("FILL_BLANK — British/American spelling normalization")
        void fillBlank_spellingNormalization() {
            assertThat(IeltsScoringUtils.isListeningCorrect("organisation", "organization", "FILL_BLANK")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("centre", "center", "FILL_BLANK")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("colour", "color", "FILL_BLANK")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("travelling", "traveling", "FILL_BLANK")).isTrue();
        }

        @Test
        @DisplayName("null or blank user answer → false")
        void nullOrBlankAnswer() {
            assertThat(IeltsScoringUtils.isListeningCorrect("A", null, "MCQ")).isFalse();
            assertThat(IeltsScoringUtils.isListeningCorrect("A", "  ", "MCQ")).isFalse();
        }
    }

    // ===================================================================
    //  Reading Answer Matching
    // ===================================================================

    @Nested
    @DisplayName("isReadingCorrect")
    class ReadingCorrect {

        @Test
        @DisplayName("TFNG — case-insensitive match")
        void tfng_caseInsensitive() {
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.TFNG, "TRUE", "true")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.TFNG, "FALSE", "true")).isFalse();
        }

        @Test
        @DisplayName("YNNG — case-insensitive match")
        void ynng_caseInsensitive() {
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.YNNG, "YES", "yes")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.YNNG, "NO", "YES")).isFalse();
        }

        @Test
        @DisplayName("MCQ — case-insensitive match")
        void mcq_match() {
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, "B", "b")).isTrue();
        }

        @Test
        @DisplayName("MATCHING types — case-insensitive")
        void matching_types() {
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MATCHING_INFORMATION, "C", "c")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MATCHING_FEATURES, "A", "a")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MATCHING_SENTENCE_ENDINGS, "D", "d")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MATCHING_HEADINGS, "iii", "III")).isTrue();
        }

        @Test
        @DisplayName("SENTENCE_COMPLETION — normalizes articles and whitespace")
        void sentenceCompletion_normalization() {
            assertThat(IeltsScoringUtils.isReadingCorrect(
                    QuestionType.SENTENCE_COMPLETION, "the global warming", "global warming")).isTrue();
            assertThat(IeltsScoringUtils.isReadingCorrect(
                    QuestionType.SENTENCE_COMPLETION, "a rapid increase", "rapid increase")).isTrue();
        }

        @Test
        @DisplayName("SUMMARY_COMPLETION — normalizes articles")
        void summaryCompletion_normalization() {
            assertThat(IeltsScoringUtils.isReadingCorrect(
                    QuestionType.SUMMARY_COMPLETION, "An important factor", "important factor")).isTrue();
        }

        @Test
        @DisplayName("FILL_BLANK — normalizes articles and whitespace")
        void fillBlank_normalization() {
            assertThat(IeltsScoringUtils.isReadingCorrect(
                    QuestionType.FILL_BLANK, "the  ocean  current", "ocean current")).isTrue();
        }

        @Test
        @DisplayName("null/blank user answer → false")
        void nullOrBlankAnswer() {
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, "A", null)).isFalse();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.MCQ, "A", "  ")).isFalse();
        }
    }

    // ===================================================================
    //  Overall Band Rounding
    // ===================================================================

    @Nested
    @DisplayName("roundOverallBand")
    class RoundOverallBand {

        @Test
        @DisplayName("fractional < 0.25 → rounds down to .0")
        void roundsDownToWhole() {
            // avg = 6.1 → 6.0
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.1")))
                    .isEqualByComparingTo(new BigDecimal("6.0"));
            // avg = 7.24 → 7.0
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("7.24")))
                    .isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        @DisplayName("fractional 0.25–0.74 → rounds to .5")
        void roundsToHalf() {
            // avg = 6.25 → 6.5
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.25")))
                    .isEqualByComparingTo(new BigDecimal("6.5"));
            // avg = 6.5 → 6.5
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.5")))
                    .isEqualByComparingTo(new BigDecimal("6.5"));
            // avg = 6.74 → 6.5
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.74")))
                    .isEqualByComparingTo(new BigDecimal("6.5"));
        }

        @Test
        @DisplayName("fractional >= 0.75 → rounds up to next .0")
        void roundsUpToNextWhole() {
            // avg = 6.75 → 7.0
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.75")))
                    .isEqualByComparingTo(new BigDecimal("7.0"));
            // avg = 6.99 → 7.0
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("6.99")))
                    .isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        @DisplayName("exact whole number → stays")
        void exactWholeNumberStays() {
            assertThat(IeltsScoringUtils.roundOverallBand(new BigDecimal("7.0")))
                    .isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        @DisplayName("real IELTS scenario: (7+6.5+7+7.5)/4 = 7.0")
        void realScenario() {
            BigDecimal avg = new BigDecimal("7.0").add(new BigDecimal("6.5"))
                    .add(new BigDecimal("7.0")).add(new BigDecimal("7.5"))
                    .divide(BigDecimal.valueOf(4), 4, java.math.RoundingMode.HALF_UP);
            assertThat(IeltsScoringUtils.roundOverallBand(avg))
                    .isEqualByComparingTo(new BigDecimal("7.0"));
        }
    }
}
