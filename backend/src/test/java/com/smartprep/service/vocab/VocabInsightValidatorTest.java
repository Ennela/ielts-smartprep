package com.smartprep.service.vocab;

import com.smartprep.dto.response.VocabInsight;
import com.smartprep.exception.InvalidAiResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VocabInsightValidator}.
 *
 * <p>The validator is what stands between a chatty model and the learner's screen, so these
 * tests are mostly about what it refuses to pass through.
 */
class VocabInsightValidatorTest {

    private final VocabInsightValidator validator = new VocabInsightValidator();

    private static VocabInsight.Sense sense(String meaningVi, VocabInsight.Example... examples) {
        return VocabInsight.Sense.builder()
                .meaningVi(meaningVi)
                .examples(new ArrayList<>(List.of(examples)))
                .build();
    }

    private static VocabInsight.Example example(String english) {
        return VocabInsight.Example.builder().english(english).vietnamese("dịch").build();
    }

    private static VocabInsight.VocabInsightBuilder valid() {
        return VocabInsight.builder()
                .word("trust")
                .coreMeaningVi("Tin tưởng vào sự đáng tin cậy của ai đó.")
                .senses(new ArrayList<>(List.of(sense("Tin ai đó.", example("I trust him.")))));
    }

    @Nested
    @DisplayName("rejects unusable payloads")
    class Rejects {

        @Test
        @DisplayName("should reject null")
        void nullPayload() {
            assertThatThrownBy(() -> validator.validate(null, "trust"))
                    .isInstanceOf(InvalidAiResponseException.class);
        }

        @Test
        @DisplayName("should reject a payload with no core Vietnamese meaning")
        void noCoreMeaning() {
            VocabInsight raw = valid().coreMeaningVi("  ").build();

            assertThatThrownBy(() -> validator.validate(raw, "trust"))
                    .isInstanceOf(InvalidAiResponseException.class)
                    .hasMessageContaining("core Vietnamese meaning");
        }

        @Test
        @DisplayName("should reject a payload whose senses are all blank")
        void noUsableSense() {
            VocabInsight raw = valid().senses(new ArrayList<>(List.of(sense("   ")))).build();

            assertThatThrownBy(() -> validator.validate(raw, "trust"))
                    .isInstanceOf(InvalidAiResponseException.class)
                    .hasMessageContaining("no usable sense");
        }

        @Test
        @DisplayName("should fall back to the saved word when the model echoed none")
        void fallbackWord() {
            VocabInsight result = validator.validate(valid().word(null).build(), "trust");

            assertThat(result.getWord()).isEqualTo("trust");
        }
    }

    @Nested
    @DisplayName("keeps the explanation readable")
    class Bounds {

        @Test
        @DisplayName("should drop an example already shown under another sense")
        void deduplicatesExamplesAcrossSenses() {
            VocabInsight raw = valid()
                    .senses(new ArrayList<>(List.of(
                            sense("Nghĩa một", example("I trust him."), example("I TRUST HIM.")),
                            sense("Nghĩa hai", example("I trust him."), example("We trust the data.")))))
                    .build();

            VocabInsight result = validator.validate(raw, "trust");

            assertThat(result.getSenses().get(0).getExamples()).hasSize(1);
            assertThat(result.getSenses().get(1).getExamples())
                    .extracting(VocabInsight.Example::getEnglish)
                    .containsExactly("We trust the data.");
        }

        @Test
        @DisplayName("should cap the number of senses and examples")
        void capsLists() {
            List<VocabInsight.Sense> senses = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                senses.add(sense("Nghĩa " + i,
                        example("Sentence " + i + "a"),
                        example("Sentence " + i + "b"),
                        example("Sentence " + i + "c"),
                        example("Sentence " + i + "d")));
            }

            VocabInsight result = validator.validate(valid().senses(senses).build(), "trust");

            assertThat(result.getSenses()).hasSize(4);
            assertThat(result.getSenses().get(0).getExamples()).hasSize(3);
        }

        @Test
        @DisplayName("should truncate an explanation that would fill the screen")
        void truncatesLongText() {
            String tooLong = "a".repeat(2000);

            VocabInsight result = validator.validate(valid().contextSummaryVi(tooLong).build(), "trust");

            assertThat(result.getContextSummaryVi()).hasSizeLessThan(700);
            assertThat(result.getContextSummaryVi()).endsWith("...");
        }

        @Test
        @DisplayName("should drop duplicate collocations")
        void deduplicatesCollocations() {
            VocabInsight raw = valid()
                    .collocations(new ArrayList<>(List.of(
                            VocabInsight.Collocation.builder().phrase("earn trust").build(),
                            VocabInsight.Collocation.builder().phrase("Earn Trust").build(),
                            VocabInsight.Collocation.builder().phrase("  ").build(),
                            VocabInsight.Collocation.builder().phrase("build trust").build())))
                    .build();

            VocabInsight result = validator.validate(raw, "trust");

            assertThat(result.getCollocations())
                    .extracting(VocabInsight.Collocation::getPhrase)
                    .containsExactly("earn trust", "build trust");
        }
    }

    @Nested
    @DisplayName("keeps only claims the product can present")
    class Claims {

        @Test
        @DisplayName("should discard register labels the interface cannot explain")
        void filtersRegisters() {
            VocabInsight raw = valid()
                    .registers(new ArrayList<>(List.of("Formal", "business", "neutral", "NEUTRAL")))
                    .build();

            VocabInsight result = validator.validate(raw, "trust");

            assertThat(result.getRegisters()).containsExactly("formal", "neutral");
        }

        @Test
        @DisplayName("should drop a synonym comparison with fewer than two words")
        void dropsSingleWordComparison() {
            VocabInsight raw = valid()
                    .synonymComparisons(new ArrayList<>(List.of(
                            VocabInsight.SynonymComparison.builder()
                                    .focus("trust alone")
                                    .words(new ArrayList<>(List.of(
                                            VocabInsight.ComparedWord.builder()
                                                    .word("trust").coreIdeaVi("tin").build())))
                                    .build(),
                            VocabInsight.SynonymComparison.builder()
                                    .focus("trust vs faith")
                                    .words(new ArrayList<>(List.of(
                                            VocabInsight.ComparedWord.builder()
                                                    .word("trust").coreIdeaVi("tin vào sự đáng tin").build(),
                                            VocabInsight.ComparedWord.builder()
                                                    .word("faith").coreIdeaVi("niềm tin sâu sắc").build())))
                                    .build())))
                    .build();

            VocabInsight result = validator.validate(raw, "trust");

            assertThat(result.getSynonymComparisons())
                    .extracting(VocabInsight.SynonymComparison::getFocus)
                    .containsExactly("trust vs faith");
        }

        @Test
        @DisplayName("should discard an invented mistake type rather than show it")
        void filtersMistakeType() {
            VocabInsight raw = valid()
                    .commonMistakes(new ArrayList<>(List.of(
                            VocabInsight.CommonMistake.builder()
                                    .incorrect("I trust on him.")
                                    .corrected("I trust him.")
                                    .type("SPELLING")
                                    .build(),
                            VocabInsight.CommonMistake.builder()
                                    .incorrect("I believe him very much.")
                                    .corrected("I really trust him.")
                                    .type("unnatural")
                                    .build())))
                    .build();

            VocabInsight result = validator.validate(raw, "trust");

            assertThat(result.getCommonMistakes().get(0).getType()).isNull();
            assertThat(result.getCommonMistakes().get(1).getType()).isEqualTo("UNNATURAL");
        }

        @Test
        @DisplayName("should omit IELTS guidance when every field is blank")
        void dropsEmptyGuidance() {
            VocabInsight raw = valid()
                    .ieltsGuidance(VocabInsight.IeltsGuidance.builder().speakingVi("  ").build())
                    .build();

            assertThat(validator.validate(raw, "trust").getIeltsGuidance()).isNull();
        }

        @Test
        @DisplayName("should leave optional sections empty rather than invent them")
        void optionalSectionsStayEmpty() {
            VocabInsight result = validator.validate(valid().build(), "trust");

            assertThat(result.getSynonymComparisons()).isEmpty();
            assertThat(result.getCollocations()).isEmpty();
            assertThat(result.getGrammarPatterns()).isEmpty();
            assertThat(result.getCommonMistakes()).isEmpty();
            assertThat(result.getIeltsGuidance()).isNull();
        }
    }
}
