package com.smartprep.service.vocab;

import com.smartprep.model.entity.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link Sm2Service}.
 * Validates the SM-2 spaced repetition algorithm:
 *   - Repetitions, interval, ease-factor updates per grade (Again/Hard/Good/Easy)
 *   - Due-date scheduling
 *   - Ease-factor floor at 1.3
 */
class Sm2ServiceTest {

    private Sm2Service sm2Service;
    private Vocabulary vocab;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 4, 12, 0, 0);

    @BeforeEach
    void setUp() {
        sm2Service = new Sm2Service();
        vocab = Vocabulary.builder()
                .word("ubiquitous")
                .meaningVi("phổ biến, ở đâu cũng có")
                .easeFactor(2.5)
                .intervalDays(0)
                .repetitions(0)
                .build();
    }

    // ===================================================================
    // First review from initial state (repetitions=0, intervalDays=0, ef=2.5)
    // ===================================================================

    @Nested
    @DisplayName("First Review (initial state)")
    class FirstReview {

        @Test
        @DisplayName("AGAIN → resets repetitions, interval=1, EF drops to floor-limited value")
        void again_resetsProgressAndDropsEF() {
            sm2Service.updateReview(vocab, "AGAIN", BASE_TIME);

            // q=1 < 3  → repetitions = 0, interval = 1
            assertThat(vocab.getRepetitions()).isZero();
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
            // EF = 2.5 + 0.1 - (5-1)*(0.08+(5-1)*0.02) = 2.5 + 0.1 - 4*0.16 = 1.96
            assertThat(vocab.getEaseFactor()).isCloseTo(1.96, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(1));
        }

        @Test
        @DisplayName("HARD → first repetition, interval=1, EF unchanged")
        void hard_firstRepetitionKeepsEF() {
            sm2Service.updateReview(vocab, "HARD", BASE_TIME);

            // q=3 >= 3  → repetitions 0→1, first interval = 1
            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
            // EF = 2.5 + 0.1 - (5-3)*(0.08+(5-3)*0.02) = 2.5 + 0.1 - 2*0.12 = 2.36
            assertThat(vocab.getEaseFactor()).isCloseTo(2.36, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(1));
        }

        @Test
        @DisplayName("GOOD → first repetition, interval=1, EF stays at 2.5")
        void good_firstRepetitionWithStableEF() {
            sm2Service.updateReview(vocab, "GOOD", BASE_TIME);

            // q=4 >= 3  → repetitions 0→1, first interval = 1
            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
            // EF = 2.5 + 0.1 - (5-4)*(0.08+(5-4)*0.02) = 2.5 + 0.1 - 1*0.10 = 2.50
            assertThat(vocab.getEaseFactor()).isCloseTo(2.50, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(1));
        }

        @Test
        @DisplayName("EASY → first repetition, interval=1, EF increases")
        void easy_firstRepetitionIncreasesEF() {
            sm2Service.updateReview(vocab, "EASY", BASE_TIME);

            // q=5 >= 3  → repetitions 0→1, first interval = 1
            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
            // EF = 2.5 + 0.1 - 0 = 2.6
            assertThat(vocab.getEaseFactor()).isCloseTo(2.6, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(1));
        }
    }

    // ===================================================================
    // Second review (repetitions=1, intervalDays=1)
    // ===================================================================

    @Nested
    @DisplayName("Second Review (repetitions=1)")
    class SecondReview {

        @BeforeEach
        void setUpSecondReview() {
            vocab.setRepetitions(1);
            vocab.setIntervalDays(1);
            vocab.setEaseFactor(2.5);
        }

        @Test
        @DisplayName("GOOD → repetitions=2, interval=6, EF stays at 2.5")
        void good_setsIntervalTo6Days() {
            sm2Service.updateReview(vocab, "GOOD", BASE_TIME);

            assertThat(vocab.getRepetitions()).isEqualTo(2);
            assertThat(vocab.getIntervalDays()).isEqualTo(6);
            assertThat(vocab.getEaseFactor()).isCloseTo(2.5, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(6));
        }

        @Test
        @DisplayName("AGAIN → resets to repetitions=0, interval=1")
        void again_resetsProgress() {
            sm2Service.updateReview(vocab, "AGAIN", BASE_TIME);

            assertThat(vocab.getRepetitions()).isZero();
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
        }
    }

    // ===================================================================
    // Third review (repetitions=2, intervalDays=6) — interval multiplied by EF
    // ===================================================================

    @Nested
    @DisplayName("Third Review (repetitions=2, uses EF multiplier)")
    class ThirdReview {

        @Test
        @DisplayName("EASY → interval=round(6*2.6)=16, EF increases to 2.7")
        void easy_multipliesIntervalByEF() {
            vocab.setRepetitions(2);
            vocab.setIntervalDays(6);
            vocab.setEaseFactor(2.6);

            sm2Service.updateReview(vocab, "EASY", BASE_TIME);

            assertThat(vocab.getRepetitions()).isEqualTo(3);
            // round(6 * 2.6) = round(15.6) = 16
            assertThat(vocab.getIntervalDays()).isEqualTo(16);
            assertThat(vocab.getEaseFactor()).isCloseTo(2.7, within(0.001));
            assertThat(vocab.getDueDate()).isEqualTo(BASE_TIME.plusDays(16));
        }

        @Test
        @DisplayName("HARD → interval=round(6*2.36), EF drops further")
        void hard_multipliesIntervalByReducedEF() {
            vocab.setRepetitions(2);
            vocab.setIntervalDays(6);
            vocab.setEaseFactor(2.5);

            sm2Service.updateReview(vocab, "HARD", BASE_TIME);

            assertThat(vocab.getRepetitions()).isEqualTo(3);
            // interval = round(6 * 2.5) = 15
            assertThat(vocab.getIntervalDays()).isEqualTo(15);
            // EF = 2.5 + 0.1 - 2*(0.08+2*0.02) = 2.5 + 0.1 - 0.24 = 2.36
            assertThat(vocab.getEaseFactor()).isCloseTo(2.36, within(0.001));
        }
    }

    // ===================================================================
    // Ease Factor floor & edge cases
    // ===================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("EF should never drop below 1.3")
        void easeFactor_neverBelowFloor() {
            vocab.setEaseFactor(1.3);
            sm2Service.updateReview(vocab, "AGAIN", BASE_TIME);

            // EF formula result would be < 1.3, but capped
            assertThat(vocab.getEaseFactor()).isEqualTo(1.3);
        }

        @Test
        @DisplayName("null grade defaults to HARD (q=3)")
        void nullGrade_defaultsToHard() {
            sm2Service.updateReview(vocab, null, BASE_TIME);

            // q=3 → same behaviour as HARD
            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getIntervalDays()).isEqualTo(1);
            assertThat(vocab.getEaseFactor()).isCloseTo(2.36, within(0.001));
        }

        @Test
        @DisplayName("unknown grade defaults to HARD (q=3)")
        void unknownGrade_defaultsToHard() {
            sm2Service.updateReview(vocab, "UNKNOWN_VALUE", BASE_TIME);

            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getEaseFactor()).isCloseTo(2.36, within(0.001));
        }

        @Test
        @DisplayName("grade matching is case-insensitive")
        void caseInsensitiveGrade() {
            sm2Service.updateReview(vocab, "easy", BASE_TIME);

            assertThat(vocab.getRepetitions()).isEqualTo(1);
            assertThat(vocab.getEaseFactor()).isCloseTo(2.6, within(0.001));
        }
    }
}
