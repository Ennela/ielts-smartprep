package com.smartprep.service;

import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.WritingFullResultResponse;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
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
 * Extracted from the original WritingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
public class WritingAssemblyService {

    private final WritingPromptRepository promptRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final WritingService writingService; // delegates grading to WritingService

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
                WritingPromptResponse.builder().promptId(t1.getPromptId()).promptText(t1.getPromptText())
                        .essayType(t1.getEssayType().name()).imageUrl(t1.getImageUrl()).build(),
                WritingPromptResponse.builder().promptId(t2.getPromptId()).promptText(t2.getPromptText())
                        .essayType(t2.getEssayType().name()).imageUrl(t2.getImageUrl()).build()
        );
    }

    @Transactional
    public WritingFullResultResponse submitFullWriting(Long userId, WritingSubmitFullRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int w1 = countWords(request.getTask1EssayText());
        int w2 = countWords(request.getTask2EssayText());
        if (w1 < MIN_WORD_COUNT_TASK1)
            throw new WordCountTooLowException("Task 1 essay must be at least " + MIN_WORD_COUNT_TASK1 + " words. Current: " + w1);
        if (w2 < MIN_WORD_COUNT_TASK2)
            throw new WordCountTooLowException("Task 2 essay must be at least " + MIN_WORD_COUNT_TASK2 + " words. Current: " + w2);

        // Delegate individual grading to WritingService
        WritingGradeResponse res1 = writingService.evaluateAndSaveSubmission(user, request.getTask1PromptId(), request.getTask1EssayText(), w1);
        WritingGradeResponse res2 = writingService.evaluateAndSaveSubmission(user, request.getTask2PromptId(), request.getTask2EssayText(), w2);

        BigDecimal weightedSum = res1.getOverallBand().add(res2.getOverallBand().multiply(BigDecimal.valueOf(2)));
        BigDecimal average = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal overallWritingBand = com.smartprep.service.util.IeltsScoringUtils.roundOverallBand(average);

        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.WRITING).score(overallWritingBand).build();
        scoreHistoryRepository.save(history);

        return WritingFullResultResponse.builder()
                .overallWritingBand(overallWritingBand)
                .task1Result(res1).task2Result(res2)
                .submittedAt(LocalDateTime.now()).build();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
