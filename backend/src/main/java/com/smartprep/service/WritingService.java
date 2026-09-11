package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.service.ai.WritingGradingService;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Writing orchestration: validates input, delegates AI grading to
 * {@link WritingGradingService}, and hands the writes to
 * {@link WritingGradingPersistence}.
 *
 * All AI logic (Gemini calls, prompt templates, JSON parsing) lives in
 * {@code service.ai.WritingGradingService}. This class owns the order of
 * operations — read, then grade, then write — which is what keeps the Gemini
 * call outside any transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingService {

    private final WritingPromptRepository promptRepository;
    private final UserRepository userRepository;
    private final WritingGradingService writingGradingService;
    private final WritingGradingPersistence gradingPersistence;
    private final ObjectMapper objectMapper;

    private static final int MIN_WORD_COUNT_TASK1 = 150;
    private static final int MIN_WORD_COUNT_TASK2 = 250;

    // ========== Grading ==========

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>The Gemini call below is allowed 65 seconds per attempt with up to three attempts.
     * Inside a transaction it held a pooled database connection for that whole time, so
     * concurrent grading exhausted the pool and blocked unrelated requests.
     *
     * <p>The reads happen first and commit, the AI call runs with nothing checked out, and
     * the writes go through {@link WritingGradingPersistence} — which still commits the
     * submission and its score-history row together, as this method used to.
     */
    public WritingGradeResponse gradeEssay(Long userId, WritingGradeRequest request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        WritingPrompt prompt = promptRepository.findById(request.getPromptId())
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found"));

        boolean isTask1 = prompt.getEssayType().isTask1();
        int minWordCount = isTask1 ? MIN_WORD_COUNT_TASK1 : MIN_WORD_COUNT_TASK2;
        int wordCount = writingGradingService.countWords(request.getEssayText());
        validateMinimumWordCount(wordCount, minWordCount, isTask1 ? "Task 1" : "Task 2");

        // No transaction is open across this call, which is the point.
        GradingResult gradingResult = writingGradingService.evaluateEssay(
                prompt.getPromptText(), request.getEssayText(), isTask1);

        return gradingPersistence.saveGradedEssay(
                userId, prompt.getPromptId(), request.getEssayText(), wordCount,
                gradingResult, parseErrorDtos(gradingResult.getErrorsJson()), true);
    }

    /**
     * Grade one essay without persisting anything.
     *
     * <p>Split out from the old {@code evaluateAndSaveSubmission} so that a caller grading
     * two essays — full-test submission — can make both AI calls before opening a
     * transaction, instead of holding one across both.
     */
    public GradingResult gradeOnly(String promptText, String essayText, boolean isTask1) {
        return writingGradingService.evaluateEssay(promptText, essayText, isTask1);
    }

    /** Parse the error DTOs an AI grading result carries, for the response body. */
    public List<WritingGradeResponse.ErrorDto> errorsOf(GradingResult gradingResult) {
        return parseErrorDtos(gradingResult.getErrorsJson());
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
