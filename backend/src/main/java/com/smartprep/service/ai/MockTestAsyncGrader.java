package com.smartprep.service.ai;

import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.*;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestAsyncGrader {

    private final MockTestSubmissionRepository submissionRepository;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final WritingGradingService gradingService;

    /**
     * Grade mock test essays asynchronously.
     */
    @Async("taskExecutor")
    @Transactional
    public void gradeWritingSubmissionsAsync(Long submissionId, String task1Essay, String task2Essay) {
        log.info("Starting asynchronous grading for MockTestSubmission ID: {}", submissionId);

        MockTestSubmission submission = submissionRepository.findById(submissionId)
                .orElse(null);
        if (submission == null) {
            log.error("MockTestSubmission with ID {} not found. Aborting async grading.", submissionId);
            return;
        }

        try {
            MockTest mockTest = submission.getMockTest();
            User user = submission.getUser();

            // 1. Identify Task 1 and Task 2 Prompts
            WritingPrompt task1Prompt = mockTest.getWritingPrompts().stream()
                    .filter(p -> p.getEssayType().isTask1())
                    .findFirst()
                    .orElse(null);

            WritingPrompt task2Prompt = mockTest.getWritingPrompts().stream()
                    .filter(p -> !p.getEssayType().isTask1())
                    .findFirst()
                    .orElse(null);

            if (task1Prompt == null || task2Prompt == null) {
                throw new IllegalStateException("Mock test does not contain both Task 1 and Task 2 prompts.");
            }

            // 2. Call Gemini API to evaluate Task 1
            log.info("Grading Writing Task 1 for submission ID: {}", submissionId);
            WritingGradingService.GradingResult task1Result = gradingService.evaluateEssay(
                    task1Prompt.getPromptText(), task1Essay, true
            );

            // 3. Call Gemini API to evaluate Task 2
            log.info("Grading Writing Task 2 for submission ID: {}", submissionId);
            WritingGradingService.GradingResult task2Result = gradingService.evaluateEssay(
                    task2Prompt.getPromptText(), task2Essay, false
            );

            // 4. Save Task 1 Writing Submission
            WritingSubmission ws1 = WritingSubmission.builder()
                    .user(user)
                    .prompt(task1Prompt)
                    .essayText(task1Essay)
                    .wordCount(task1Result.getWordCount())
                    .overallBand(task1Result.getOverallBand())
                    .taskResponseScore(task1Result.getTaskResponse())
                    .coherenceScore(task1Result.getCoherence())
                    .lexicalScore(task1Result.getLexical())
                    .grammarScore(task1Result.getGrammar())
                    .errorListJson(task1Result.getErrorsJson())
                    .rewrittenVersion(task1Result.getRewrittenEssay())
                    .aiFeedback(task1Result.getGeneralFeedback())
                    .build();
            ws1 = writingSubmissionRepository.save(ws1);

            // 5. Save Task 2 Writing Submission
            WritingSubmission ws2 = WritingSubmission.builder()
                    .user(user)
                    .prompt(task2Prompt)
                    .essayText(task2Essay)
                    .wordCount(task2Result.getWordCount())
                    .overallBand(task2Result.getOverallBand())
                    .taskResponseScore(task2Result.getTaskResponse())
                    .coherenceScore(task2Result.getCoherence())
                    .lexicalScore(task2Result.getLexical())
                    .grammarScore(task2Result.getGrammar())
                    .errorListJson(task2Result.getErrorsJson())
                    .rewrittenVersion(task2Result.getRewrittenEssay())
                    .aiFeedback(task2Result.getGeneralFeedback())
                    .build();
            ws2 = writingSubmissionRepository.save(ws2);

            // 6. Aggregate Writing Score: Task 2 is 2/3, Task 1 is 1/3
            BigDecimal w1Score = task1Result.getOverallBand();
            BigDecimal w2Score = task2Result.getOverallBand();
            BigDecimal weightedSum = w1Score.add(w2Score.multiply(BigDecimal.valueOf(2)));
            BigDecimal writingAverage = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            BigDecimal roundedWritingScore = IeltsScoringUtils.roundOverallBand(writingAverage);

            // 7. Aggregate Overall Band Score (L, R, W average)
            BigDecimal lScore = submission.getListeningScore();
            BigDecimal rScore = submission.getReadingScore();
            BigDecimal overallSum = lScore.add(rScore).add(roundedWritingScore);
            BigDecimal overallAverage = overallSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            BigDecimal roundedOverallBand = IeltsScoringUtils.roundOverallBand(overallAverage);

            // 8. Update Mock Test Submission
            submission.setWritingTask1Submission(ws1);
            submission.setWritingTask2Submission(ws2);
            submission.setWritingScore(roundedWritingScore);
            submission.setOverallBand(roundedOverallBand);
            submission.setStatus(SubmissionStatus.COMPLETED);
            submissionRepository.save(submission);

            log.info("Asynchronous grading successfully completed for MockTestSubmission ID: {}. Overall Band: {}",
                    submissionId, roundedOverallBand);

        } catch (Exception e) {
            log.error("Error occurred during async grading for MockTestSubmission ID: {}", submissionId, e);
            submission.setStatus(SubmissionStatus.FAILED);
            submissionRepository.save(submission);
        }
    }
}
