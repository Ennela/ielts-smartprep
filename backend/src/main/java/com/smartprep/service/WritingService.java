package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.ai.WritingGradingService;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Writing orchestration: validates input, delegates AI grading to
 * {@link WritingGradingService}, and persists submissions + score history.
 *
 * All AI logic (Gemini calls, prompt templates, JSON parsing) lives in
 * {@code service.ai.WritingGradingService} — this class only handles
 * persistence and response mapping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private final WritingPromptRepository promptRepository;
    private final WritingSubmissionRepository submissionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final WritingGradingService writingGradingService;
    private final WritingQueryService writingQueryService;
    private final ObjectMapper objectMapper;

    private static final int MIN_WORD_COUNT_TASK1 = 150;
    private static final int MIN_WORD_COUNT_TASK2 = 250;

    // ========== Grading ==========

    @Transactional
    public WritingGradeResponse gradeEssay(Long userId, WritingGradeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        WritingPrompt prompt = promptRepository.findById(request.getPromptId())
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found"));

        boolean isTask1 = prompt.getEssayType().isTask1();
        int minWordCount = isTask1 ? MIN_WORD_COUNT_TASK1 : MIN_WORD_COUNT_TASK2;
        int wordCount = writingGradingService.countWords(request.getEssayText());
        validateMinimumWordCount(wordCount, minWordCount, isTask1 ? "Task 1" : "Task 2");

        WritingGradeResponse result = evaluateAndSaveSubmission(user, prompt, request.getEssayText(), wordCount);

        // Save to score history
        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.WRITING).score(result.getOverallBand()).build();
        scoreHistoryRepository.save(history);

        return result;
    }

    /**
     * Evaluate a single essay via AI and persist the submission.
     * Public so that WritingAssemblyService can call it for full-test submissions.
     */
    @Transactional
    public WritingGradeResponse evaluateAndSaveSubmission(User user, Long promptId, String essayText, int wordCount) {
        WritingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found: " + promptId));
        return evaluateAndSaveSubmission(user, prompt, essayText, wordCount);
    }

    @Transactional
    public WritingGradeResponse evaluateAndSaveSubmission(User user, WritingPrompt prompt, String essayText,
            int wordCount) {
        boolean isTask1 = prompt.getEssayType().isTask1();

        // Delegate AI grading to service.ai.WritingGradingService
        GradingResult gradingResult = writingGradingService.evaluateEssay(
                prompt.getPromptText(), essayText, isTask1);

        // Parse error DTOs from the serialized JSON for the response
        List<WritingGradeResponse.ErrorDto> errors = parseErrorDtos(gradingResult.getErrorsJson());

        // Persist submission
        WritingSubmission submission = WritingSubmission.builder()
                .user(user).prompt(prompt).essayText(essayText).wordCount(wordCount)
                .overallBand(gradingResult.getOverallBand())
                .taskResponseScore(gradingResult.getTaskResponse())
                .coherenceScore(gradingResult.getCoherence())
                .lexicalScore(gradingResult.getLexical())
                .grammarScore(gradingResult.getGrammar())
                .errorListJson(gradingResult.getErrorsJson())
                .rewrittenVersion(gradingResult.getRewrittenEssay())
                .aiFeedback(gradingResult.getGeneralFeedback())
                .build();

        submission = submissionRepository.save(submission);
        return writingQueryService.buildGradeResponse(
                submission, prompt, errors, gradingResult.getImprovementNotes());
    }

    // ========== Private Helpers ==========

    private List<WritingGradeResponse.ErrorDto> parseErrorDtos(String errorsJson) {
        if (errorsJson == null || errorsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(errorsJson,
                    new TypeReference<List<WritingGradeResponse.ErrorDto>>() {
                    });
        } catch (Exception e) {
            log.warn("Failed to parse errors JSON from AI grading result: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void validateMinimumWordCount(int wordCount, int minWordCount, String taskLabel) {
        if (wordCount < minWordCount) {
            throw new WordCountTooLowException(
                    taskLabel + " requires at least " + minWordCount + " words. Current word count: " + wordCount + ".");
        }
    }
}
