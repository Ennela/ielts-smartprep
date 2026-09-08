package com.smartprep.service;

import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.WritingFullResultResponse;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingFullSubmission;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingFullSubmissionRepository;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles full Writing mock tests and orchestrates multi-task submissions.
 */
@Service
@RequiredArgsConstructor
public class WritingAssemblyService {

    private final WritingPromptRepository promptRepository;
    private final UserRepository userRepository;
    private final WritingService writingService;
    private final WritingGradingPersistence gradingPersistence;
    private final WritingQueryService writingQueryService;
    private final WritingFullSubmissionRepository writingFullSubmissionRepository;

    private static final int MIN_WORD_COUNT_TASK1 = 150;
    private static final int MIN_WORD_COUNT_TASK2 = 250;

    @Transactional(readOnly = true)
    public List<WritingPromptResponse> assembleMockTest() {
        List<WritingPrompt> allPrompts = promptRepository.findAll();
        List<WritingPrompt> task1Prompts = allPrompts.stream()
                .filter(p -> p.getEssayType().isTask1()).collect(Collectors.toList());
        List<WritingPrompt> task2Prompts = allPrompts.stream()
                .filter(p -> !p.getEssayType().isTask1()).collect(Collectors.toList());

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
                        .taskType(t1.getTaskType() != null ? t1.getTaskType().name() : null)
                        .imageUrl(t1.getImageUrl())
                        .visualData(t1.getVisualData())
                        .build(),
                WritingPromptResponse.builder()
                        .promptId(t2.getPromptId())
                        .promptText(t2.getPromptText())
                        .essayType(t2.getEssayType().name())
                        .taskType(t2.getTaskType() != null ? t2.getTaskType().name() : null)
                        .imageUrl(t2.getImageUrl())
                        .visualData(t2.getVisualData())
                        .build()
        );
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>This was the worst remaining instance of the pattern: one transaction spanning
     * <em>two</em> Gemini calls, each allowed 65 seconds with up to three attempts. A single
     * full-test submission could hold a pooled database connection for minutes.
     *
     * <p>The order is now read, grade, write. Both essays are graded with nothing checked
     * out of the pool, and every row this produces — the two submissions, the completed exam
     * attempt, the aggregate row and the score history — is still written in one transaction
     * inside {@link WritingGradingPersistence#saveFullWriting}, so a sitting cannot be left
     * half recorded.
     */
    public WritingFullResultResponse submitFullWriting(Long userId, WritingSubmitFullRequest request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int w1 = countWords(request.getTask1EssayText());
        int w2 = countWords(request.getTask2EssayText());
        validateMinimumWordCount(w1, MIN_WORD_COUNT_TASK1, "Task 1");
        validateMinimumWordCount(w2, MIN_WORD_COUNT_TASK2, "Task 2");

        WritingPrompt prompt1 = promptRepository.findById(request.getTask1PromptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Writing prompt not found: " + request.getTask1PromptId()));
        WritingPrompt prompt2 = promptRepository.findById(request.getTask2PromptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Writing prompt not found: " + request.getTask2PromptId()));

        // Both AI calls happen here, with no transaction open.
        GradingResult result1 = writingService.gradeOnly(
                prompt1.getPromptText(), request.getTask1EssayText(), true);
        GradingResult result2 = writingService.gradeOnly(
                prompt2.getPromptText(), request.getTask2EssayText(), false);

        return gradingPersistence.saveFullWriting(
                userId, request, w1, w2, result1, result2,
                writingService.errorsOf(result1), writingService.errorsOf(result2));
    }

    @Transactional(readOnly = true)
    public List<WritingFullResultResponse> getFullSubmissionsHistory(Long userId) {
        return writingFullSubmissionRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(this::toFullResultResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WritingFullResultResponse getFullSubmission(Long userId, Long id) {
        WritingFullSubmission fullSub = writingFullSubmissionRepository.findByIdAndUserUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Full writing submission not found"));
        return toFullResultResponse(fullSub);
    }

    private WritingFullResultResponse toFullResultResponse(WritingFullSubmission fullSub) {
        return WritingFullResultResponse.builder()
                .id(fullSub.getId())
                .overallWritingBand(fullSub.getOverallBand())
                .task1Result(writingQueryService.getSubmission(fullSub.getUser().getUserId(), fullSub.getTask1Submission().getSubmissionId()))
                .task2Result(writingQueryService.getSubmission(fullSub.getUser().getUserId(), fullSub.getTask2Submission().getSubmissionId()))
                .submittedAt(fullSub.getSubmittedAt())
                .timeSpentSeconds(fullSub.getTimeSpentSeconds())
                .timeSpentTask1(fullSub.getTimeSpentTask1())
                .timeSpentTask2(fullSub.getTimeSpentTask2())
                .autoSubmitted(fullSub.getAutoSubmitted())
                .build();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private void validateMinimumWordCount(int wordCount, int minWordCount, String taskLabel) {
        if (wordCount < minWordCount) {
            throw new WordCountTooLowException(
                    taskLabel + " requires at least " + minWordCount + " words. Current word count: " + wordCount + ".");
        }
    }
}
