package com.smartprep.service.util;

import com.smartprep.model.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules for written answers: Reading completion questions and every Listening question
 * that is not a letter choice.
 *
 * <p>Half of these tests are about what must <em>not</em> match. Making the comparison more
 * forgiving is only an improvement if it stays exactly as strict about wrong answers.
 */
class WrittenAnswerMatchingTest {

    @Nested
    @DisplayName("accepts what an examiner would accept")
    class Accepts {

        @ParameterizedTest(name = "\"{1}\" for \"{0}\"")
        @CsvSource(delimiter = ';', value = {
                // punctuation the candidate added
                "paint ; paint.",
                "paint ; 'paint'",
                "rainfall ; rainfall!",
                // alternatives the stored answer lists
                "coal / charcoal ; coal",
                "coal / charcoal ; charcoal",
                "coal|charcoal ; charcoal",
                // numbers written either way
                "eighty-five ; 85",
                "Forty-seven ; 47",
                "twelve thousand ; 12000",
                "twelve thousand ; 12,000",
                "three ; 3",
                "one hundred and five ; 105",
                "85 ; eighty five",
                "three bedrooms ; 3 bedrooms",
                // hyphens and spaces
                "twenty-five ; twenty five",
                "well-known ; well known",
                // articles and spelling variants, as before
                "the station ; station",
                "colour ; color",
                "city centre ; city center",
                // apostrophes
                "children's books ; childrens books",
        })
        void written(String stored, String typed) {
            assertThat(IeltsScoringUtils.writtenAnswerMatches(stored, typed)).isTrue();
        }
    }

    @Nested
    @DisplayName("still rejects wrong answers")
    class Rejects {

        @ParameterizedTest(name = "\"{1}\" for \"{0}\"")
        @CsvSource(delimiter = ';', value = {
                "paint ; paints",
                "coal / charcoal ; coal charcoal",
                "coal / charcoal ; wood",
                "eighty-five ; 58",
                "eighty-five ; 80",
                "twelve thousand ; 1200",
                "three bedrooms ; 3",
                "station ; stations",
        })
        void written(String stored, String typed) {
            assertThat(IeltsScoringUtils.writtenAnswerMatches(stored, typed)).isFalse();
        }

        @Test
        @DisplayName("does not split a slash between numbers into two accepted answers")
        void numericSlashStaysWhole() {
            // "24/7" is one answer. Splitting it would accept "7" on its own.
            assertThat(IeltsScoringUtils.acceptedForms("24/7")).containsExactly("24/7");
            assertThat(IeltsScoringUtils.writtenAnswerMatches("24/7", "7")).isFalse();
            assertThat(IeltsScoringUtils.acceptedForms("1/2")).containsExactly("1/2");
        }

        @Test
        @DisplayName("treats a blank or punctuation-only answer as no answer")
        void blank() {
            assertThat(IeltsScoringUtils.writtenAnswerMatches("paint", "...")).isFalse();
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.FILL_BLANK, "paint", "  ")).isFalse();
        }
    }

    @Nested
    @DisplayName("enforces NO MORE THAN N WORDS")
    class WordLimit {

        @Test
        @DisplayName("an answer over the limit is wrong even when it contains the right words")
        void overTheLimit() {
            assertThat(IeltsScoringUtils.isReadingCorrect(
                    QuestionType.SENTENCE_COMPLETION, "solar power", "solar power", 2)).isTrue();
            assertThat(IeltsScoringUtils.exceedsWordLimit("clean solar power", 2)).isTrue();
        }

        @Test
        @DisplayName("numbers do not count towards the limit")
        void numbersAreFree() {
            assertThat(IeltsScoringUtils.exceedsWordLimit("3 bedrooms", 1)).isFalse();
            assertThat(IeltsScoringUtils.exceedsWordLimit("twelve thousand people", 1)).isFalse();
            assertThat(IeltsScoringUtils.exceedsWordLimit("twenty-five kilometres", 1)).isFalse();
        }

        @Test
        @DisplayName("a hyphenated word counts once")
        void hyphenatedWordIsOne() {
            assertThat(IeltsScoringUtils.exceedsWordLimit("well-known author", 2)).isFalse();
        }

        @Test
        @DisplayName("a leading article does not count, because the comparison ignores it too")
        void leadingArticleIsFree() {
            assertThat(IeltsScoringUtils.exceedsWordLimit("the station", 1)).isFalse();
        }

        @Test
        @DisplayName("no limit means no limit")
        void noLimit() {
            assertThat(IeltsScoringUtils.exceedsWordLimit("a very long answer indeed", null)).isFalse();
            assertThat(IeltsScoringUtils.exceedsWordLimit("a very long answer indeed", 0)).isFalse();
        }

        @Test
        @DisplayName("is applied to Reading completion questions and nothing else")
        void onlyCompletion() {
            // A TFNG answer is a single token anyway, but the limit must not be consulted.
            assertThat(IeltsScoringUtils.isReadingCorrect(QuestionType.TFNG, "NOT GIVEN", "NOT GIVEN", 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("Listening")
    class Listening {

        @Test
        @DisplayName("a number written in digits matches one stored in words")
        void numbers() {
            assertThat(IeltsScoringUtils.isListeningCorrect("eighty-five", "85", "FILL_BLANK")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("twelve thousand", "12,000", "FILL_BLANK")).isTrue();
        }

        @Test
        @DisplayName("MCQ letters still compare exactly")
        void mcq() {
            assertThat(IeltsScoringUtils.isListeningCorrect("B", "b", "MCQ")).isTrue();
            assertThat(IeltsScoringUtils.isListeningCorrect("B", "B.", "MCQ")).isFalse();
        }
    }
}
