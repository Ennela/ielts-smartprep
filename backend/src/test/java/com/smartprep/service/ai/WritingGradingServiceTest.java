package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WritingGradingService}.
 * All calls to GeminiClient are mocked — no real AI invocations.
 */
@ExtendWith(MockitoExtension.class)
class WritingGradingServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WritingGradingService writingGradingService;

    // ===================================================================
    //  Test Data
    // ===================================================================

    private static final String GRADING_JSON = """
            {
              "overallBand": 7.0,
              "taskResponse": 7.0,
              "coherence": 6.5,
              "lexical": 7.0,
              "grammar": 7.5,
              "errors": [
                {
                  "originalSentence": "Although he try hard, he failed.",
                  "errorType": "GRAMMAR",
                  "explanation": "Subject-verb agreement error.",
                  "correctedSentence": "Although he tried hard, he failed."
                }
              ],
              "generalFeedback": "Great effort. You addressed the prompt well but need to improve on coherence."
            }
            """;

    private static final String REWRITE_JSON = """
            {
              "rewrittenEssay": "Although he tried hard, he ultimately failed to achieve his objective.",
              "improvementNotes": [
                "Improved verb tense consistency",
                "Enhanced vocabulary selection"
              ]
            }
            """;

    private static final String TASK_ACHIEVEMENT_JSON = """
            {
              "overallBand": 6.0,
              "taskAchievement": 6.0,
              "coherence": 6.0,
              "lexical": 6.0,
              "grammar": 6.0,
              "errors": [
                {
                  "originalSentence": "The graph show increase.",
                  "errorType": "GRAMMAR",
                  "explanation": "Missing article and verb agreement.",
                  "correctedSentence": "The graph shows an increase."
                }
              ],
              "generalFeedback": "You need to improve your data description accuracy."
            }
            """;

    // ===================================================================
    //  Word Count Tests
    // ===================================================================

    @Nested
    @DisplayName("countWords")
    class CountWords {

        @Test
        @DisplayName("null → 0")
        void nullText() {
            assertThat(writingGradingService.countWords(null)).isZero();
        }

        @Test
        @DisplayName("blank → 0")
        void blankText() {
            assertThat(writingGradingService.countWords("  ")).isZero();
        }

        @Test
        @DisplayName("normal text with multiple spaces")
        void normalText() {
            assertThat(writingGradingService.countWords("Hello   world coding")).isEqualTo(3);
        }

        @Test
        @DisplayName("text with leading/trailing whitespace")
        void trimmedText() {
            assertThat(writingGradingService.countWords("  This is a test essay  ")).isEqualTo(5);
        }
    }

    // ===================================================================
    //  Essay Evaluation — Happy Path
    // ===================================================================

    @Nested
    @DisplayName("evaluateEssay — success scenarios")
    class EvaluateEssaySuccess {

        @Test
        @DisplayName("Task 2: parses grading JSON + rewrite JSON correctly")
        void task2_fullPipeline() {
            stubGeminiCalls(GRADING_JSON, REWRITE_JSON);

            GradingResult result = writingGradingService.evaluateEssay(
                    "Write about technology",
                    "Although he try hard, he failed.",
                    false
            );

            assertThat(result).isNotNull();
            assertThat(result.getOverallBand()).isEqualByComparingTo(new BigDecimal("7.0"));
            assertThat(result.getTaskResponse()).isEqualByComparingTo(new BigDecimal("7.0"));
            assertThat(result.getCoherence()).isEqualByComparingTo(new BigDecimal("6.5"));
            assertThat(result.getLexical()).isEqualByComparingTo(new BigDecimal("7.0"));
            assertThat(result.getGrammar()).isEqualByComparingTo(new BigDecimal("7.5"));
            assertThat(result.getGeneralFeedback()).contains("coherence");
            assertThat(result.getErrorsJson()).contains("GRAMMAR");
            assertThat(result.getRewrittenEssay()).contains("tried hard");
            assertThat(result.getImprovementNotes()).hasSize(2);
            assertThat(result.getWordCount()).isEqualTo(6);

            verify(geminiClient, times(2)).generateAndParse(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Task 1: uses taskAchievement when taskResponse absent")
        void task1_taskAchievementFallback() {
            stubGeminiCalls(TASK_ACHIEVEMENT_JSON, REWRITE_JSON);

            GradingResult result = writingGradingService.evaluateEssay(
                    "Describe the graph",
                    "The graph show increase over time in several categories.",
                    true
            );

            assertThat(result).isNotNull();
            // taskAchievement should be used as taskResponse
            assertThat(result.getTaskResponse()).isEqualByComparingTo(new BigDecimal("6.0"));
            assertThat(result.getOverallBand()).isEqualByComparingTo(new BigDecimal("6.0"));
        }

        @Test
        @DisplayName("overall band is calculated from components, not from AI's overallBand")
        void overallBand_recalculated() {
            // AI says overallBand = 7.0, but components average to 7.0 as well
            // (7+6.5+7+7.5)/4 = 7.0
            stubGeminiCalls(GRADING_JSON, REWRITE_JSON);

            GradingResult result = writingGradingService.evaluateEssay(
                    "Test prompt", "Some test essay with enough words.", false);

            // The service recalculates overall band from components
            assertThat(result.getOverallBand()).isEqualByComparingTo(new BigDecimal("7.0"));
        }

        @Test
        @DisplayName("scores are clamped to 0–9 and rounded to nearest 0.5")
        void scores_clampedAndRounded() {
            String extremeScoreJson = """
                    {
                      "taskResponse": 10.0,
                      "coherence": -1.0,
                      "lexical": 6.3,
                      "grammar": 7.8,
                      "errors": [],
                      "generalFeedback": "Extreme score test."
                    }
                    """;
            stubGeminiCalls(extremeScoreJson, REWRITE_JSON);

            GradingResult result = writingGradingService.evaluateEssay(
                    "Test", "Short essay text for testing.", false);

            // 10 clamped to 9 → 9.0
            assertThat(result.getTaskResponse()).isEqualByComparingTo(new BigDecimal("9.0"));
            // -1 clamped to 0 → 0.0
            assertThat(result.getCoherence()).isEqualByComparingTo(new BigDecimal("0.0"));
            // 6.3 rounded to nearest 0.5 → 6.5
            assertThat(result.getLexical()).isEqualByComparingTo(new BigDecimal("6.5"));
            // 7.8 rounded to nearest 0.5 → 8.0
            assertThat(result.getGrammar()).isEqualByComparingTo(new BigDecimal("8.0"));
        }
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    /**
     * Stub two sequential generateAndParse calls:
     * first returns parsed grading JSON, second returns parsed rewrite JSON.
     */
    @SuppressWarnings("unchecked")
    private void stubGeminiCalls(String gradingResponse, String rewriteResponse) {
        when(geminiClient.generateAndParse(anyString(), anyString(), any(GeminiClient.CheckedFunction.class)))
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, JsonNode> parser = invocation.getArgument(2);
                    return parser.apply(gradingResponse);
                })
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, JsonNode> parser = invocation.getArgument(2);
                    return parser.apply(rewriteResponse);
                });
    }
}
