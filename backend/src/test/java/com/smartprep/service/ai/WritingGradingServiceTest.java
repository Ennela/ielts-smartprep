package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WritingGradingServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WritingGradingService writingGradingService;

    private String gradingJsonResponse;
    private String rewriteJsonResponse;

    @BeforeEach
    void setUp() {
        gradingJsonResponse = """
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

        rewriteJsonResponse = """
                {
                  "rewrittenEssay": "Although he tried hard, he ultimately failed to achieve his objective.",
                  "improvementNotes": [
                    "Improved verb tense consistency",
                    "Enhanced vocabulary selection"
                  ]
                }
                """;
    }

    @Test
    @DisplayName("should count words correctly including multiple spaces")
    void countWords_validText_returnsCorrectCount() {
        assertEquals(0, writingGradingService.countWords(""));
        assertEquals(0, writingGradingService.countWords(null));
        assertEquals(3, writingGradingService.countWords("Hello   world coding"));
        assertEquals(5, writingGradingService.countWords("  This is a test essay  "));
    }

    @Test
    @DisplayName("should evaluate essay and return grading and rewrite results")
    void evaluateEssay_success() throws Exception {
        // Stub the first call (grading) and second call (rewrite)
        JsonNode gradingJsonNode = objectMapper.readTree(gradingJsonResponse);
        JsonNode rewriteJsonNode = objectMapper.readTree(rewriteJsonResponse);

        when(geminiClient.generateAndParse(anyString(), anyString(), any(GeminiClient.CheckedFunction.class)))
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, JsonNode> parser = invocation.getArgument(2);
                    return parser.apply(gradingJsonResponse);
                })
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, JsonNode> parser = invocation.getArgument(2);
                    return parser.apply(rewriteJsonResponse);
                });

        GradingResult result = writingGradingService.evaluateEssay(
                "Write about technology",
                "Although he try hard, he failed.",
                false
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("7.0"), result.getOverallBand());
        assertEquals(new BigDecimal("7.0"), result.getTaskResponse());
        assertEquals(new BigDecimal("6.5"), result.getCoherence());
        assertEquals(new BigDecimal("7.0"), result.getLexical());
        assertEquals(new BigDecimal("7.5"), result.getGrammar());
        assertEquals("Great effort. You addressed the prompt well but need to improve on coherence.", result.getGeneralFeedback());
        assertTrue(result.getErrorsJson().contains("GRAMMAR"));
        assertEquals("Although he tried hard, he ultimately failed to achieve his objective.", result.getRewrittenEssay());
        assertEquals(2, result.getImprovementNotes().size());
        assertEquals(6, result.getWordCount());

        verify(geminiClient, times(2)).generateAndParse(anyString(), anyString(), any());
    }
}
