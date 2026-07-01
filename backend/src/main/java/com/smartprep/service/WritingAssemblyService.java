package com.smartprep.service;

import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.WritingFullResultResponse;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.ExamAttempt;
import com.smartprep.model.entity.WritingFullSubmission;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingFullSubmissionRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final WritingService writingService;
    private final WritingQueryService writingQueryService;
    private final WritingFullSubmissionRepository writingFullSubmissionRepository;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final ExamAttemptService examAttemptService;

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
                        .imageUrl(t1.getImageUrl())
                        .visualData(t1.getVisualData())
                        .build(),
                WritingPromptResponse.builder()
                        .promptId(t2.getPromptId())
                        .promptText(t2.getPromptText())
                        .essayType(t2.getEssayType().name())
                        .imageUrl(t2.getImageUrl())
                        .visualData(t2.getVisualData())
                        .build()
        );
    }

    @Transactional
    public WritingFullResultResponse submitFullWriting(Long userId, WritingSubmitFullRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int w1 = countWords(request.getTask1EssayText());
        int w2 = countWords(request.getTask2EssayText());

        // Delegate individual grading to WritingService
        WritingGradeResponse res1 = writingService.evaluateAndSaveSubmission(user, request.getTask1PromptId(), request.getTask1EssayText(), w1);
        WritingGradeResponse res2 = writingService.evaluateAndSaveSubmission(user, request.getTask2PromptId(), request.getTask2EssayText(), w2);

        BigDecimal weightedSum = res1.getOverallBand().add(res2.getOverallBand().multiply(BigDecimal.valueOf(2)));
        BigDecimal average = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal overallWritingBand = com.smartprep.service.util.IeltsScoringUtils.roundOverallBand(average);

        // Persist individual submissions reference
        WritingSubmission sub1 = writingSubmissionRepository.findById(res1.getSubmissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Task 1 submission not found after creation"));
        WritingSubmission sub2 = writingSubmissionRepository.findById(res2.getSubmissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Task 2 submission not found after creation"));

        // Complete exam attempt if provided (must happen BEFORE building fullSub)
        ExamAttempt completedAttempt = null;
        boolean autoSubmitted = request.getAutoSubmitted() != null && request.getAutoSubmitted();
        if (request.getAttemptId() != null) {
            completedAttempt = examAttemptService.completeAttemptInternal(
                    request.getAttemptId(), userId, autoSubmitted,
                    request.getTimeSpentTask1(), request.getTimeSpentTask2());
        }

        Integer timeSpentSeconds = completedAttempt != null ? completedAttempt.getTimeSpentSeconds() : null;
        Integer timeSpentTask1 = request.getTimeSpentTask1();
        Integer timeSpentTask2 = request.getTimeSpentTask2();

        WritingFullSubmission fullSub = WritingFullSubmission.builder()
                .user(user)
                .task1Submission(sub1)
                .task2Submission(sub2)
                .overallBand(overallWritingBand)
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build();
        fullSub = writingFullSubmissionRepository.save(fullSub);

        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.WRITING).score(overallWritingBand)
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build();
        scoreHistoryRepository.save(history);

        return WritingFullResultResponse.builder()
                .id(fullSub.getId())
                .overallWritingBand(overallWritingBand)
                .task1Result(res1)
                .task2Result(res2)
                .submittedAt(fullSub.getSubmittedAt())
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build();
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
}
