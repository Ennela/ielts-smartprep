package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.dto.response.WritingHistoryResponse;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.dto.response.WritingFullResultResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import java.time.LocalDateTime;
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

    /**
     * Assemble one random Task 1 prompt and one random Task 2 prompt for a full practice test.
     */
    @Transactional(readOnly = true)
    public List<WritingPromptResponse> assembleMockTest() {
        List<WritingPrompt> allPrompts = promptRepository.findAll();

        List<WritingPrompt> task1Prompts = allPrompts.stream()
                .filter(p -> p.getEssayType().isTask1())
                .collect(Collectors.toList());

        List<WritingPrompt> task2Prompts = allPrompts.stream()
                .filter(p -> !p.getEssayType().isTask1())
                .collect(Collectors.toList());

        if (task1Prompts.isEmpty() || task2Prompts.isEmpty()) {
            throw new ResourceNotFoundException("Not enough prompts in the database to assemble a full Writing test.");
        }

        java.util.Random rand = new java.util.Random();
        WritingPrompt t1 = task1Prompts.get(rand.nextInt(task1Prompts.size()));
        WritingPrompt t2 = task2Prompts.get(rand.nextInt(task2Prompts.size()));

        return List.of(
                WritingPromptResponse.builder()
                        .promptId(t1.getPromptId())
                        .promptText(t1.getPromptText())
                        .essayType(t1.getEssayType().name())
                        .imageUrl(t1.getImageUrl())
                        .build(),
                WritingPromptResponse.builder()
                        .promptId(t2.getPromptId())
                        .promptText(t2.getPromptText())
                        .essayType(t2.getEssayType().name())
                        .imageUrl(t2.getImageUrl())
                        .build()
        );
    }

    /**
     * Submit essays for a full Writing test (both Task 1 and Task 2 prompts).
     * Calculates the weighted Writing band score: (Task 1 + 2 * Task 2) / 3.
     */
    @Transactional
    public WritingFullResultResponse submitFullWriting(Long userId, WritingSubmitFullRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        WritingPrompt prompt1 = promptRepository.findById(request.getTask1PromptId())
                .orElseThrow(() -> new ResourceNotFoundException("Writing Task 1 prompt not found: " + request.getTask1PromptId()));
        WritingPrompt prompt2 = promptRepository.findById(request.getTask2PromptId())
                .orElseThrow(() -> new ResourceNotFoundException("Writing Task 2 prompt not found: " + request.getTask2PromptId()));

        int w1 = countWords(request.getTask1EssayText());
        int w2 = countWords(request.getTask2EssayText());

        if (w1 < MIN_WORD_COUNT_TASK1) {
            throw new WordCountTooLowException("Task 1 essay must be at least " + MIN_WORD_COUNT_TASK1 + " words. Current: " + w1);
        }
        if (w2 < MIN_WORD_COUNT_TASK2) {
            throw new WordCountTooLowException("Task 2 essay must be at least " + MIN_WORD_COUNT_TASK2 + " words. Current: " + w2);
        }

        // Evaluate Task 1
        WritingGradeResponse res1 = evaluateAndSaveSubmission(user, prompt1, request.getTask1EssayText(), w1);

        // Evaluate Task 2
        WritingGradeResponse res2 = evaluateAndSaveSubmission(user, prompt2, request.getTask2EssayText(), w2);

        // Calculate weighted score (Task 2 is double weighted)
        BigDecimal weightedSum = res1.getOverallBand().add(res2.getOverallBand().multiply(BigDecimal.valueOf(2)));
        BigDecimal average = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal overallWritingBand = com.smartprep.service.util.IeltsScoringUtils.roundOverallBand(average);

        // Save a single entry to score history representing this full Writing test attempt
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.WRITING)
                .score(overallWritingBand)
                .build();
        scoreHistoryRepository.save(history);

        return WritingFullResultResponse.builder()
                .overallWritingBand(overallWritingBand)
                .task1Result(res1)
                .task2Result(res2)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    private WritingGradeResponse evaluateAndSaveSubmission(User user, WritingPrompt prompt, String essayText, int wordCount) {
        boolean isTask1 = prompt.getEssayType().isTask1();

        // Step 1: Call AI for grading
        String systemPrompt = isTask1 ? TASK1_GRADING_SYSTEM_PROMPT : GRADING_SYSTEM_PROMPT;
        String gradingResponse = geminiClient.generate(
                systemPrompt,
                buildGradingUserPrompt(prompt.getPromptText(), essayText, isTask1)
        );
        JsonNode gradingJson = parseJson(gradingResponse);

        BigDecimal taskResponse = extractScore(gradingJson, "taskResponse");
        BigDecimal coherence = extractScore(gradingJson, "coherence");
        BigDecimal lexical = extractScore(gradingJson, "lexical");
        BigDecimal grammar = extractScore(gradingJson, "grammar");
        BigDecimal overallBand = calculateOverallBand(taskResponse, coherence, lexical, grammar);

        List<WritingGradeResponse.ErrorDto> errors = extractErrors(gradingJson);
        String generalFeedback = gradingJson.path("generalFeedback").asText("");

        // Step 2: Call AI for rewrite
        String rewriteSystemPrompt = isTask1 ? TASK1_REWRITE_SYSTEM_PROMPT : REWRITE_SYSTEM_PROMPT;
        String rewriteResponse = geminiClient.generate(
                rewriteSystemPrompt,
                buildRewriteUserPrompt(prompt.getPromptText(), essayText, gradingResponse, isTask1)
        );
        JsonNode rewriteJson = parseJson(rewriteResponse);

        String rewrittenEssay = rewriteJson.path("rewrittenEssay").asText("");
        List<String> improvementNotes = extractImprovementNotes(rewriteJson);

        String errorListJson;
        try {
            errorListJson = objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            errorListJson = "[]";
        }

        WritingSubmission submission = WritingSubmission.builder()
                .user(user)
                .prompt(prompt)
                .essayText(essayText)
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

    // ========== Task 1 AI Prompt Templates ==========

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
