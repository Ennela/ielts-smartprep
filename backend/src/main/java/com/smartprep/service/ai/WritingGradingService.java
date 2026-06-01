package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingGradingService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    // Standard IELTS minimum word requirements
    private static final int MIN_WORD_COUNT_TASK1 = 150;
    private static final int MIN_WORD_COUNT_TASK2 = 250;

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GradingResult {
        private BigDecimal overallBand;
        private BigDecimal taskResponse;
        private BigDecimal coherence;
        private BigDecimal lexical;
        private BigDecimal grammar;
        private String generalFeedback;
        private String errorsJson; // Serialized JSON string of errors list
        private String rewrittenEssay;
        private List<String> improvementNotes;
        private int wordCount;
    }

    public int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    public GradingResult evaluateEssay(String promptText, String essayText, boolean isTask1) {
        int wordCount = countWords(essayText);

        // Step 1: Call AI for grading
        String systemPrompt = isTask1 ? TASK1_GRADING_SYSTEM_PROMPT : GRADING_SYSTEM_PROMPT;
        String userPrompt = String.format(
            "IELTS Writing %s Prompt:\n\"%s\"\n\nStudent Essay:\n%s",
            isTask1 ? "Task 1" : "Task 2", promptText, essayText
        );

        String gradingResponse = geminiClient.generate(systemPrompt, userPrompt);
        JsonNode gradingJson = parseJson(gradingResponse);

        // Extract scores
        BigDecimal taskResponse = extractScore(gradingJson, "taskResponse");
        BigDecimal coherence = extractScore(gradingJson, "coherence");
        BigDecimal lexical = extractScore(gradingJson, "lexical");
        BigDecimal grammar = extractScore(gradingJson, "grammar");
        
        // Calculate overall band using standard IELTS formula
        BigDecimal overallBand = calculateOverallBand(taskResponse, coherence, lexical, grammar);

        // Extract error list
        JsonNode errorsNode = gradingJson.path("errors");
        String errorsJsonString;
        try {
            errorsJsonString = objectMapper.writeValueAsString(errorsNode);
        } catch (Exception e) {
            errorsJsonString = "[]";
        }

        String generalFeedback = gradingJson.path("generalFeedback").asText("");

        // Step 2: Call AI for rewritten essay
        String rewriteSystemPrompt = isTask1 ? TASK1_REWRITE_SYSTEM_PROMPT : REWRITE_SYSTEM_PROMPT;
        String rewriteUserPrompt = String.format(
            "Original IELTS Writing %s Prompt:\n\"%s\"\n\n"
            + "Original Student Essay:\n%s\n\n"
            + "Error Analysis and Scores:\n%s",
            isTask1 ? "Task 1" : "Task 2", promptText, essayText, gradingResponse
        );

        String rewriteResponse = geminiClient.generate(rewriteSystemPrompt, rewriteUserPrompt);
        JsonNode rewriteJson = parseJson(rewriteResponse);

        String rewrittenEssay = rewriteJson.path("rewrittenEssay").asText("");
        
        List<String> improvementNotes = new ArrayList<>();
        JsonNode notesNode = rewriteJson.path("improvementNotes");
        if (notesNode.isArray()) {
            for (JsonNode n : notesNode) {
                improvementNotes.add(n.asText());
            }
        }

        return GradingResult.builder()
                .overallBand(overallBand)
                .taskResponse(taskResponse)
                .coherence(coherence)
                .lexical(lexical)
                .grammar(grammar)
                .generalFeedback(generalFeedback)
                .errorsJson(errorsJsonString)
                .rewrittenEssay(rewrittenEssay)
                .improvementNotes(improvementNotes)
                .wordCount(wordCount)
                .build();
    }

    private JsonNode parseJson(String response) {
        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response: {}", response);
            throw new InvalidAiResponseException("AI returned invalid JSON: " + e.getMessage());
        }
    }

    private BigDecimal extractScore(JsonNode json, String field) {
        double score = json.path(field).asDouble(5.0);
        score = Math.max(0, Math.min(9, score));
        score = Math.round(score * 2) / 2.0;
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateOverallBand(BigDecimal tr, BigDecimal cc, BigDecimal lr, BigDecimal gra) {
        BigDecimal sum = tr.add(cc).add(lr).add(gra);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);
        return com.smartprep.service.util.IeltsScoringUtils.roundOverallBand(avg);
    }

    // ========== AI System Prompts (Task 1 & Task 2) ==========

    private static final String GRADING_SYSTEM_PROMPT = """
            You are an official IELTS Writing examiner with 15 years of experience.
            Grade the following IELTS Writing Task 2 essay using the 4 official IELTS band descriptors.

            === GRADING PROCESS (follow in this order) ===
            STEP 1: Read the essay carefully and thoroughly.
            STEP 2: For each of the 4 criteria below, determine which band (4, 5, 6, 7, 8, or 9) best matches the essay, based ONLY on the rubric descriptions. Do NOT guess or default to any specific number.
            STEP 3: Convert your band determination to a numeric score (must be a multiple of 0.5, range 1.0–9.0).
            STEP 4: Calculate Overall Band = average of 4 component scores, using IELTS rounding:
              * fractional part < 0.25 → round down to .0
              * fractional part 0.25–0.74 → round to .5
              * fractional part >= 0.75 → round up to next .0
            STEP 5: Output the final JSON below.

            === OUTPUT FORMAT ===
            You MUST return valid JSON with this exact structure (replace YOUR_SCORE with actual numeric values you determined in STEP 3):
            {
              "overallBand": YOUR_SCORE,
              "taskResponse": YOUR_SCORE,
              "coherence": YOUR_SCORE,
              "lexical": YOUR_SCORE,
              "grammar": YOUR_SCORE,
              "errors": [
                {
                  "originalSentence": "<exact sentence from student's essay>",
                  "errorType": "GRAMMAR",
                  "explanation": "<why this is wrong and what rule is violated>",
                  "correctedSentence": "<corrected version>"
                }
              ],
              "generalFeedback": "<2-3 paragraphs of overall feedback with specific improvement suggestions referencing the rubric>"
            }

            === IELTS WRITING TASK 2 BAND DESCRIPTORS RUBRIC ===

            ### Task Response (TR) — assess this criterion independently:
            - Band 9: Fully addresses ALL parts of the task. Presents a fully developed position with relevant, fully extended and well supported ideas.
            - Band 8: Sufficiently addresses all parts. Presents a well-developed response with relevant, extended and supported ideas.
            - Band 7: Addresses all parts. Clear position throughout. Main ideas are presented, extended and supported, but may over-generalise or lack focus.
            - Band 6: Addresses all parts, though some may be more fully covered. Position is relevant but conclusions may become unclear/repetitive. Some main ideas inadequately developed.
            - Band 5: Addresses the task only PARTIALLY. Position expressed but development unclear, may have no conclusion. Main ideas limited and insufficiently developed; irrelevant detail possible.
            - Band 4 or below: Responds minimally or tangentially. Position unclear. Ideas difficult to identify, repetitive or not supported.

            ### Coherence and Cohesion (CC) — assess this criterion independently:
            - Band 9: Cohesion attracts no attention. Paragraphing skilfully managed.
            - Band 8: Information sequenced logically. All cohesion aspects managed well. Paragraphing sufficient and appropriate.
            - Band 7: Information logically organised with clear progression. Cohesive devices used appropriately (minor under-/over-use possible). Clear central topic per paragraph.
            - Band 6: Coherent overall progression. Cohesive devices effective but cohesion within/between sentences may be faulty or mechanical. Referencing not always clear. Paragraphing not always logical.
            - Band 5: Some organisation but lacks overall progression. Cohesive devices inadequate, inaccurate or overused. May be repetitive due to lack of referencing. Paragraphing inadequate or absent.
            - Band 4 or below: Not arranged coherently, no clear progression, confusing or no paragraphing.

            ### Lexical Resource (LR) — assess this criterion independently:
            - Band 9: Wide range of vocabulary with very natural, sophisticated control. Rare minor errors as 'slips' only.
            - Band 8: Wide range used fluently and flexibly for precise meanings. Uncommon items used skilfully (occasional inaccuracies in collocation). Rare spelling/word-formation errors.
            - Band 7: Sufficient range for some flexibility and precision. Less common items used with some style/collocation awareness. Occasional errors in word choice, spelling or word formation.
            - Band 6: Adequate range for the task. Attempts less common vocabulary with some inaccuracy. Some spelling/word-formation errors that do NOT impede communication.
            - Band 5: Limited range, minimally adequate. Noticeable spelling/word-formation errors that may cause difficulty.
            - Band 4 or below: Only basic vocabulary, used repetitively or inappropriately. Limited control of word formation/spelling; errors cause strain or distort meaning.

            ### Grammatical Range and Accuracy (GRA) — assess this criterion independently:
            - Band 9: Wide range of structures with full flexibility and accuracy. Rare minor errors as 'slips' only.
            - Band 8: Wide range of structures. Majority of sentences error-free. Very occasional errors/inappropriacies.
            - Band 7: Variety of complex structures. Frequent error-free sentences. Good grammar/punctuation control with a few errors.
            - Band 6: Mix of simple and complex forms. Some grammar/punctuation errors that RARELY reduce communication.
            - Band 5: Limited range. Complex sentences tend to be less accurate than simple ones. Frequent grammatical errors; punctuation may be faulty.
            - Band 4 or below: Very limited range with rare subordinate clauses. Errors predominate; punctuation often faulty.

            === ERROR ANALYSIS ===
            ERROR TYPES: GRAMMAR, VOCABULARY, COHERENCE, SPELLING, PUNCTUATION
            - List at least 3 specific errors if overall band is below 7.0.
            - List at least 5 specific errors if overall band is below 6.0.
            - Quote the EXACT original sentence from the essay.
            - Provide a clear corrected version with explanation.

            Return ONLY valid JSON. No markdown, no code fences, no extra text.
            """;

    private static final String REWRITE_SYSTEM_PROMPT = """
            You are an expert IELTS Writing tutor and Band 8.5+ writer.
            Based on the student's original essay and the error analysis provided,
            rewrite a complete, improved version of the essay.

            REWRITING RULES:
            - Keep the SAME ideas, arguments, and examples from the original essay.
            - Upgrade sentence structures: use complex/compound-complex sentences, participle clauses, and inversions.
            - Upgrade vocabulary: replace basic words with C1-C2 academic vocabulary.
            - Improve coherence: add sophisticated linking devices and ensure smooth paragraph transitions.
            - Maintain the same essay structure (introduction, body paragraphs, conclusion).
            - Target Band 8.0+ quality.

            You MUST return valid JSON with this exact structure:
            {
              "rewrittenEssay": "<full rewritten essay, 250-350 words>",
              "improvementNotes": [
                "<specific note about what was improved and why>",
                "<another improvement note>"
              ]
            }

            Provide 3-5 improvement notes highlighting the key upgrades made.
            Return ONLY valid JSON. No markdown, no code fences, no extra text.
            """;

    private static final String TASK1_GRADING_SYSTEM_PROMPT = """
            You are an official IELTS Writing examiner with 15 years of experience.
            Grade the following IELTS Academic Writing Task 1 report using the 4 official IELTS Task 1 band descriptors.
            Task 1 requires the student to describe visual data (graphs, charts, tables, diagrams, or maps).

            === GRADING PROCESS (follow in this order) ===
            STEP 1: Read the report carefully and thoroughly.
            STEP 2: For each of the 4 criteria below, determine which band (4, 5, 6, 7, 8, or 9) best matches the report, based ONLY on the rubric descriptions. Do NOT guess or default to any specific number.
            STEP 3: Convert your band determination to a numeric score (must be a multiple of 0.5, range 1.0–9.0).
            STEP 4: Calculate Overall Band = average of 4 component scores, using IELTS rounding:
              * fractional part < 0.25 → round down to .0
              * fractional part 0.25–0.74 → round to .5
              * fractional part >= 0.75 → round up to next .0
            STEP 5: Output the final JSON below.

            === OUTPUT FORMAT ===
            You MUST return valid JSON with this exact structure (replace YOUR_SCORE with actual numeric values you determined in STEP 3):
            {
              "overallBand": YOUR_SCORE,
              "taskResponse": YOUR_SCORE,
              "coherence": YOUR_SCORE,
              "lexical": YOUR_SCORE,
              "grammar": YOUR_SCORE,
              "errors": [
                {
                  "originalSentence": "<exact sentence from student's report>",
                  "errorType": "GRAMMAR",
                  "explanation": "<why this is wrong and what rule is violated>",
                  "correctedSentence": "<corrected version>"
                }
              ],
              "generalFeedback": "<2-3 paragraphs of overall feedback analyzing task achievement, overview clarity, data accuracy, and comparisons, referencing the rubric>"
            }

            === IELTS ACADEMIC WRITING TASK 1 BAND DESCRIPTORS RUBRIC ===

            ### Task Achievement (TA) — assess this criterion independently:
            - Band 9: Fully satisfies ALL requirements of the task. Clearly presents a fully developed response.
            - Band 8: Covers all requirements sufficiently. Presents, highlights and illustrates key features clearly and appropriately.
            - Band 7: Covers the requirements. Presents a CLEAR OVERVIEW of main trends, differences or stages. Highlights key features but could be more fully extended.
            - Band 6: Addresses the requirements. Overview present with appropriate selection. Adequately highlights key features but some details may be irrelevant, inappropriate or inaccurate.
            - Band 5: Generally addresses the task. Recounts detail MECHANICALLY with NO CLEAR OVERVIEW; may lack data support. Inadequately covers key features; tends to focus on details rather than trends.
            - Band 4 or below: Attempts the task but fails to cover all key features; confuses features with details; parts unclear, irrelevant, repetitive or inaccurate.

            ### Coherence and Cohesion (CC) — assess this criterion independently:
            - Band 9: Cohesion attracts no attention. Paragraphing skilfully managed.
            - Band 8: Information sequenced logically. All cohesion aspects managed well. Paragraphing sufficient and appropriate.
            - Band 7: Information logically organised with clear progression. Cohesive devices used appropriately (minor under-/over-use possible).
            - Band 6: Coherent overall progression. Cohesive devices effective but cohesion within/between sentences may be faulty or mechanical. Referencing not always clear.
            - Band 5: Some organisation but lacks overall progression. Cohesive devices inadequate, inaccurate or overused. May be repetitive due to lack of referencing.
            - Band 4 or below: Not arranged coherently, no clear progression, basic cohesive devices inaccurate or repetitive.

            ### Lexical Resource (LR) — assess this criterion independently:
            - Band 9: Wide range of vocabulary with very natural, sophisticated control. Rare minor errors as 'slips' only.
            - Band 8: Wide range used fluently and flexibly for precise meanings. Uncommon items used skilfully (occasional inaccuracies in collocation). Rare spelling/word-formation errors.
            - Band 7: Sufficient range for flexibility and precision. Less common items used with some style/collocation awareness. Occasional errors in word choice, spelling or word formation.
            - Band 6: Adequate range. Attempts less common vocabulary with some inaccuracy. Some spelling/word-formation errors that do NOT impede communication.
            - Band 5: Limited range, minimally adequate. Noticeable spelling/word-formation errors that may cause difficulty.
            - Band 4 or below: Only basic vocabulary, used repetitively or inappropriately. Limited control of word formation/spelling.

            ### Grammatical Range and Accuracy (GRA) — assess this criterion independently:
            - Band 9: Wide range of structures with full flexibility and accuracy. Rare minor errors as 'slips' only.
            - Band 8: Wide range of structures. Majority of sentences error-free. Very occasional errors/inappropriacies.
            - Band 7: Variety of complex structures. Frequent error-free sentences. Good grammar/punctuation control with a few errors.
            - Band 6: Mix of simple and complex forms. Some grammar/punctuation errors that RARELY reduce communication.
            - Band 5: Limited range. Complex sentences tend to be less accurate than simple ones. Frequent grammatical errors; punctuation may be faulty.
            - Band 4 or below: Very limited range with rare subordinate clauses. Errors predominate; punctuation often faulty.

            === ERROR ANALYSIS ===
            ERROR TYPES: GRAMMAR, VOCABULARY, COHERENCE, SPELLING, PUNCTUATION
            - List at least 3 specific errors if overall band is below 7.0.
            - List at least 5 specific errors if overall band is below 6.0.
            - Quote the EXACT original sentence from the report.
            - Provide a clear corrected version with explanation.

            Return ONLY valid JSON. No markdown, no code fences, no extra text.
            """;

    private static final String TASK1_REWRITE_SYSTEM_PROMPT = """
            You are an expert IELTS Writing tutor and Band 8.5+ writer.
            Based on the student''s original Writing Task 1 report and the error analysis provided,
            rewrite a complete, improved version of the report.

            REWRITING RULES:
            - Accurately describe the visual data (numbers, trends, comparisons).
            - Include a clear introduction (paraphrasing the prompt) and a distinct overview of the main trends.
            - Use high-quality academic vocabulary for describing changes, trends, and comparisons
              (e.g., "witnessed a dramatic surge", "remained relatively stable", "accounted for the largest proportion").
            - Keep the same information but express it with sophisticated cohesion and complex structures.
            - Target Band 8.0+ quality.
            - The rewritten report should be 150-200 words.

            You MUST return valid JSON with this exact structure:
            {
              "rewrittenEssay": "<full rewritten report, 150-200 words>",
              "improvementNotes": [
                "<specific note about what was improved in data presentation or trend descriptions>",
                "<another improvement note>"
              ]
            }

            Provide 3-5 improvement notes highlighting the key upgrades made.
            Return ONLY valid JSON. No markdown, no code fences, no extra text.
            """;
}
