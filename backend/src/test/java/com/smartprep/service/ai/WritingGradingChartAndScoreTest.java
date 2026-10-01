package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The grader sees the Task 1 chart (P1 #16), and a score that is not a number is rejected
 * instead of becoming a silent 5.0 (W6).
 */
@ExtendWith(MockitoExtension.class)
class WritingGradingChartAndScoreTest {

    @Mock private GeminiClient geminiClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private WritingGradingService writingGradingService;

    private static WritingPrompt prompt(EssayType type, String visualData, String imageUrl) {
        return WritingPrompt.builder().promptText("The chart below shows rainfall.")
                .essayType(type).visualData(visualData).imageUrl(imageUrl).build();
    }

    @Nested
    @DisplayName("promptForGrading")
    class PromptForGrading {

        @Test
        @DisplayName("hands the grader the chart data of a Task 1 prompt")
        void includesData() {
            String data = "{\"labels\":[\"2000\",\"2010\"],\"values\":[12,30]}";
            String text = WritingGradingService.promptForGrading(prompt(EssayType.LINE_GRAPH, data, null));

            assertThat(text).startsWith("The chart below shows rainfall.").contains(data);
        }

        @Test
        @DisplayName("tells the grader when the chart is an image it cannot see")
        void imageOnly() {
            String text = WritingGradingService.promptForGrading(
                    prompt(EssayType.LINE_GRAPH, null, "https://example.test/chart.png"));

            assertThat(text).contains("not available to you").contains("Do not mark the figures");
        }

        @Test
        @DisplayName("leaves a Task 2 prompt alone")
        void task2() {
            WritingPrompt p = prompt(EssayType.OPINION, "{\"stray\":1}", "https://example.test/x.png");

            assertThat(WritingGradingService.promptForGrading(p)).isEqualTo("The chart below shows rainfall.");
        }

        @Test
        @DisplayName("leaves a Task 1 prompt with no chart at all alone")
        void task1WithoutChart() {
            assertThat(WritingGradingService.promptForGrading(prompt(EssayType.LINE_GRAPH, " ", null)))
                    .isEqualTo("The chart below shows rainfall.");
        }
    }

    @Nested
    @DisplayName("band scores from the model")
    class Scores {

        private static final String ESSAY = "word ".repeat(40);

        @SuppressWarnings("unchecked")
        private void modelAlwaysReturns(String json) {
            when(geminiClient.gradeAndParse(anyString(), anyString(), any(GeminiClient.CheckedFunction.class)))
                    .thenAnswer(invocation -> {
                        GeminiClient.CheckedFunction<String, JsonNode> parser = invocation.getArgument(2);
                        return parser.apply(json);
                    });
        }

        @Test
        @DisplayName("a score that is not a number fails the response instead of defaulting to 5.0")
        void nonNumericScoreIsRejected() {
            modelAlwaysReturns("""
                    {"taskResponse": 7, "coherence": "seven", "lexical": 6, "grammar": 6}
                    """);

            assertThatThrownBy(() -> writingGradingService.evaluateEssay("prompt", ESSAY, false))
                    .isInstanceOf(InvalidAiResponseException.class)
                    .hasMessageContaining("coherence");
        }

        @Test
        @DisplayName("a null score is rejected too")
        void nullScoreIsRejected() {
            modelAlwaysReturns("""
                    {"taskResponse": 7, "coherence": null, "lexical": 6, "grammar": 6}
                    """);

            assertThatThrownBy(() -> writingGradingService.evaluateEssay("prompt", ESSAY, false))
                    .isInstanceOf(InvalidAiResponseException.class);
        }
    }
}
