package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.dto.response.WritingHistoryResponse;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private final WritingPromptRepository promptRepository;
    private final WritingSubmissionRepository submissionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final int MIN_WORD_COUNT_TASK1 = 150;
    private static final int MIN_WORD_COUNT_TASK2 = 250;

    // ========== Prompt Listing ==========

    @Transactional(readOnly = true)
    public List<WritingPromptResponse> getPrompts(String essayTypeFilter) {
        List<WritingPrompt> prompts;
        if (essayTypeFilter != null && !essayTypeFilter.isBlank()) {
            EssayType type = EssayType.valueOf(essayTypeFilter.toUpperCase().trim());
            prompts = promptRepository.findByEssayTypeOrderByCreatedAtDesc(type);
        } else {
            prompts = promptRepository.findAllByOrderByCreatedAtDesc();
        }

        return prompts.stream()
                .map(p -> WritingPromptResponse.builder()
                        .promptId(p.getPromptId())
                        .promptText(p.getPromptText())
                        .essayType(p.getEssayType().name())
                        .imageUrl(p.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WritingPromptResponse getPromptById(Long promptId) {
        WritingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found"));
        return WritingPromptResponse.builder()
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
                .imageUrl(prompt.getImageUrl())
                .build();
    }

    // ========== Grading ==========

    @Transactional
    public WritingGradeResponse gradeEssay(Long userId, WritingGradeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        WritingPrompt prompt = promptRepository.findById(request.getPromptId())
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found"));

        // Validate word count (Task 1: 150 words, Task 2: 250 words)
        boolean isTask1 = prompt.getEssayType().isTask1();
        int minWordCount = isTask1 ? MIN_WORD_COUNT_TASK1 : MIN_WORD_COUNT_TASK2;
        int wordCount = countWords(request.getEssayText());
        if (wordCount < minWordCount) {
            throw new WordCountTooLowException(
                    "Essay must be at least " + minWordCount + " words. Current: " + wordCount);
        }

        // Step 1: Call AI for grading (select task-specific system prompt)
        String systemPrompt = isTask1 ? TASK1_GRADING_SYSTEM_PROMPT : GRADING_SYSTEM_PROMPT;
        String gradingResponse = geminiClient.generate(
                systemPrompt,
                buildGradingUserPrompt(prompt.getPromptText(), request.getEssayText(), isTask1)
        );
        JsonNode gradingJson = parseJson(gradingResponse);

        // Extract scores
        BigDecimal taskResponse = extractScore(gradingJson, "taskResponse");
        BigDecimal coherence = extractScore(gradingJson, "coherence");
        BigDecimal lexical = extractScore(gradingJson, "lexical");
        BigDecimal grammar = extractScore(gradingJson, "grammar");
        BigDecimal overallBand = calculateOverallBand(taskResponse, coherence, lexical, grammar);

        // Extract errors
        List<WritingGradeResponse.ErrorDto> errors = extractErrors(gradingJson);
        String generalFeedback = gradingJson.path("generalFeedback").asText("");

        // Step 2: Call AI for rewritten essay
        String rewriteSystemPrompt = isTask1 ? TASK1_REWRITE_SYSTEM_PROMPT : REWRITE_SYSTEM_PROMPT;
        String rewriteResponse = geminiClient.generate(
                rewriteSystemPrompt,
                buildRewriteUserPrompt(prompt.getPromptText(), request.getEssayText(), gradingResponse, isTask1)
        );
        JsonNode rewriteJson = parseJson(rewriteResponse);

        String rewrittenEssay = rewriteJson.path("rewrittenEssay").asText("");
        List<String> improvementNotes = extractImprovementNotes(rewriteJson);

        // Save submission
        String errorListJson;
        try {
            errorListJson = objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            errorListJson = "[]";
        }

        WritingSubmission submission = WritingSubmission.builder()
                .user(user)
                .prompt(prompt)
                .essayText(request.getEssayText())
                .wordCount(wordCount)
                .overallBand(overallBand)
                .taskResponseScore(taskResponse)
                .coherenceScore(coherence)
                .lexicalScore(lexical)
                .grammarScore(grammar)
                .errorListJson(errorListJson)
                .rewrittenVersion(rewrittenEssay)
                .aiFeedback(generalFeedback)
                .build();

        submission = submissionRepository.save(submission);

        // Save to score history
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.WRITING)
                .score(overallBand)
                .build();
        scoreHistoryRepository.save(history);

        return buildGradeResponse(submission, prompt, errors, improvementNotes);
    }

    // ========== History ==========

    @Transactional(readOnly = true)
    public List<WritingHistoryResponse> getHistory(Long userId) {
        return submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(s -> WritingHistoryResponse.builder()
                        .submissionId(s.getSubmissionId())
                        .promptId(s.getPrompt().getPromptId())
                        .essayType(s.getPrompt().getEssayType().name())
                        .promptTextPreview(truncate(s.getPrompt().getPromptText(), 100))
                        .overallBand(s.getOverallBand())
                        .wordCount(s.getWordCount())
                        .submittedAt(s.getSubmittedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WritingGradeResponse getSubmission(Long userId, Long submissionId) {
        WritingSubmission submission = submissionRepository
                .findBySubmissionIdAndUserUserId(submissionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        WritingPrompt prompt = submission.getPrompt();

        // Parse stored errors
        List<WritingGradeResponse.ErrorDto> errors;
        try {
            errors = objectMapper.readValue(
                    submission.getErrorListJson(),
                    new TypeReference<List<WritingGradeResponse.ErrorDto>>() {}
            );
        } catch (Exception e) {
            errors = new ArrayList<>();
        }

        return buildGradeResponse(submission, prompt, errors, null);
    }

    // ========== IELTS Band Score Rounding Algorithm ==========

    /**
     * Calculates the overall IELTS band score from 4 component scores.
     * IELTS rounding rules:
     * - .0 and .5 stay as-is
     * - .25 rounds up to .5
     * - .75 rounds up to next .0
     * - Otherwise round down
     */
    public static BigDecimal calculateOverallBand(BigDecimal tr, BigDecimal cc, BigDecimal lr, BigDecimal gra) {
        BigDecimal sum = tr.add(cc).add(lr).add(gra);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);

        // Get the integer part and fractional part
        double avgDouble = avg.doubleValue();
        int intPart = (int) avgDouble;
        double fracPart = avgDouble - intPart;

        double rounded;
        if (fracPart < 0.25) {
            rounded = intPart;
        } else if (fracPart < 0.75) {
            rounded = intPart + 0.5;
        } else {
            rounded = intPart + 1.0;
        }

        return BigDecimal.valueOf(rounded).setScale(1, RoundingMode.HALF_UP);
    }

    // ========== Private Helpers ==========

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private JsonNode parseJson(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response: {}", response);
            throw new InvalidAiResponseException("AI returned invalid JSON: " + e.getMessage());
        }
    }

    private BigDecimal extractScore(JsonNode json, String field) {
        double score = json.path(field).asDouble(5.0);
        // Ensure score is valid IELTS band (0-9, step 0.5)
        score = Math.max(0, Math.min(9, score));
        score = Math.round(score * 2) / 2.0;
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }

    private List<WritingGradeResponse.ErrorDto> extractErrors(JsonNode json) {
        List<WritingGradeResponse.ErrorDto> errors = new ArrayList<>();
        JsonNode errorsNode = json.path("errors");
        if (errorsNode.isArray()) {
            for (JsonNode e : errorsNode) {
                errors.add(WritingGradeResponse.ErrorDto.builder()
                        .originalSentence(e.path("originalSentence").asText(""))
                        .errorType(e.path("errorType").asText("GRAMMAR"))
                        .explanation(e.path("explanation").asText(""))
                        .correctedSentence(e.path("correctedSentence").asText(""))
                        .build());
            }
        }
        return errors;
    }

    private List<String> extractImprovementNotes(JsonNode json) {
        List<String> notes = new ArrayList<>();
        JsonNode notesNode = json.path("improvementNotes");
        if (notesNode.isArray()) {
            for (JsonNode n : notesNode) {
                notes.add(n.asText());
            }
        }
        return notes;
    }

    private WritingGradeResponse buildGradeResponse(
            WritingSubmission submission,
            WritingPrompt prompt,
            List<WritingGradeResponse.ErrorDto> errors,
            List<String> improvementNotes) {
        return WritingGradeResponse.builder()
                .submissionId(submission.getSubmissionId())
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
                .essayText(submission.getEssayText())
                .wordCount(submission.getWordCount())
                .overallBand(submission.getOverallBand())
                .taskResponse(submission.getTaskResponseScore())
                .coherence(submission.getCoherenceScore())
                .lexical(submission.getLexicalScore())
                .grammar(submission.getGrammarScore())
                .errors(errors)
                .generalFeedback(submission.getAiFeedback())
                .rewrittenVersion(submission.getRewrittenVersion())
                .improvementNotes(improvementNotes)
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private String buildGradingUserPrompt(String promptText, String essayText, boolean isTask1) {
        String taskLabel = isTask1 ? "Task 1" : "Task 2";
        return String.format(
                "IELTS Writing %s Prompt:\n\"%s\"\n\nStudent Essay:\n%s",
                taskLabel, promptText, essayText);
    }

    private String buildRewriteUserPrompt(String promptText, String essayText, String errorAnalysis, boolean isTask1) {
        String taskLabel = isTask1 ? "Task 1" : "Task 2";
        return String.format(
                "Original IELTS Writing %s Prompt:\n\"%s\"\n\n"
                + "Original Student Essay:\n%s\n\n"
                + "Error Analysis and Scores:\n%s",
                taskLabel, promptText, essayText, errorAnalysis);
    }

    // ========== AI Prompt Templates ==========

    private static final String GRADING_SYSTEM_PROMPT = """
            You are an official IELTS Writing examiner with 15 years of experience.
            Grade the following IELTS Writing Task 2 essay using the 4 official IELTS band descriptors.

            You MUST return valid JSON with this exact structure:
            {
              "overallBand": 6.5,
              "taskResponse": 6.5,
              "coherence": 6.0,
              "lexical": 6.5,
              "grammar": 6.0,
              "errors": [
                {
                  "originalSentence": "<exact sentence from student's essay>",
                  "errorType": "GRAMMAR",
                  "explanation": "<why this is wrong and what rule is violated>",
                  "correctedSentence": "<corrected version>"
                }
              ],
              "generalFeedback": "<2-3 paragraphs of overall feedback with specific improvement suggestions>"
            }

            SCORING RULES:
            - All scores must be multiples of 0.5 (e.g., 5.0, 5.5, 6.0, 6.5, 7.0).
            - Score range: 1.0 to 9.0.
            - Overall Band = average of 4 component scores, rounded per IELTS rules:
              * If fractional part is .25, round UP to .5
              * If fractional part is .75, round UP to next .0
              * Otherwise keep as .0 or .5
            - Be strict but fair. Most intermediate learners score between 5.0-6.5.

            ERROR TYPES: GRAMMAR, VOCABULARY, COHERENCE, SPELLING, PUNCTUATION

            ERROR ANALYSIS RULES:
            - List at least 3 errors if band is below 7.0.
            - List at least 5 errors if band is below 6.0.
            - Quote the EXACT original sentence from the essay.
            - Provide a clear corrected version.

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

    // ========== Task 1 AI Prompt Templates ==========

    private static final String TASK1_GRADING_SYSTEM_PROMPT = """
            You are an official IELTS Writing examiner with 15 years of experience.
            Grade the following IELTS Academic Writing Task 1 report using the 4 official IELTS Task 1 band descriptors.
            Task 1 requires the student to describe visual data (graphs, charts, tables, diagrams, or maps).

            You MUST return valid JSON with this exact structure:
            {
              "overallBand": 6.5,
              "taskResponse": 6.5,
              "coherence": 6.0,
              "lexical": 6.5,
              "grammar": 6.0,
              "errors": [
                {
                  "originalSentence": "<exact sentence from student's report>",
                  "errorType": "GRAMMAR",
                  "explanation": "<why this is wrong and what rule is violated>",
                  "correctedSentence": "<corrected version>"
                }
              ],
              "generalFeedback": "<2-3 paragraphs of overall feedback analyzing task achievement, overview clarity, data accuracy, and comparisons>"
            }

            TASK 1 SCORING CRITERIA:
            - taskResponse = Task Achievement: Does the report accurately describe main trends/features? Is there a clear overview? Are key data points selected and compared?
            - coherence = Coherence & Cohesion: Is the report logically organized? Are linking devices used effectively?
            - lexical = Lexical Resource: Does the student use appropriate vocabulary for describing trends, data, and comparisons?
            - grammar = Grammatical Range & Accuracy: Does the student use a variety of sentence structures correctly?

            SCORING RULES:
            - All scores must be multiples of 0.5 (e.g., 5.0, 5.5, 6.0, 6.5, 7.0).
            - Score range: 1.0 to 9.0.
            - Overall Band = average of 4 component scores, rounded per IELTS rules:
              * If fractional part is .25, round UP to .5
              * If fractional part is .75, round UP to next .0
              * Otherwise keep as .0 or .5
            - Be strict but fair. Most intermediate learners score between 5.0-6.5.
            - Minimum word count is 150 words.

            ERROR TYPES: GRAMMAR, VOCABULARY, COHERENCE, SPELLING, PUNCTUATION

            ERROR ANALYSIS RULES:
            - List at least 3 errors if band is below 7.0.
            - List at least 5 errors if band is below 6.0.
            - Quote the EXACT original sentence from the report.
            - Provide a clear corrected version.

            Return ONLY valid JSON. No markdown, no code fences, no extra text.
            """;

    private static final String TASK1_REWRITE_SYSTEM_PROMPT = """
            You are an expert IELTS Writing tutor and Band 8.5+ writer.
            Based on the student's original Writing Task 1 report and the error analysis provided,
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
