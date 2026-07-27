package com.smartprep.service.util;

import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.model.entity.QuestionOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QuestionOptionMapper}.
 * The exam view must never carry the answer key; the review view must.
 */
class QuestionOptionMapperTest {

    private List<QuestionOption> twoOptions() {
        return List.of(
                QuestionOption.builder().optionId(1L).label("A").content("alpha").isCorrect(false).orderIndex(1).build(),
                QuestionOption.builder().optionId(2L).label("B").content("beta").isCorrect(true).orderIndex(2).build());
    }

    @Nested
    @DisplayName("mapForExam")
    class MapForExam {

        @Test
        @DisplayName("drops isCorrect so the answer key never reaches the question paper")
        void dropsIsCorrect() {
            assertThat(QuestionOptionMapper.mapForExam(twoOptions()))
                    .allSatisfy(o -> assertThat(o.getIsCorrect()).isNull());
        }

        @Test
        @DisplayName("keeps label and content")
        void keepsLabelAndContent() {
            List<QuestionOptionResponse> mapped = QuestionOptionMapper.mapForExam(twoOptions());
            assertThat(mapped).extracting(QuestionOptionResponse::getLabel).containsExactly("A", "B");
            assertThat(mapped).extracting(QuestionOptionResponse::getContent).containsExactly("alpha", "beta");
            assertThat(mapped).extracting(QuestionOptionResponse::getOptionId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("returns null for null input")
        void nullSafe() {
            assertThat(QuestionOptionMapper.mapForExam(null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapForReview")
    class MapForReview {

        @Test
        @DisplayName("keeps isCorrect so the result screen can mark the right option")
        void keepsIsCorrect() {
            assertThat(QuestionOptionMapper.mapForReview(twoOptions()))
                    .extracting(QuestionOptionResponse::getIsCorrect)
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("returns null for null input")
        void nullSafe() {
            assertThat(QuestionOptionMapper.mapForReview(null)).isNull();
        }
    }
}
