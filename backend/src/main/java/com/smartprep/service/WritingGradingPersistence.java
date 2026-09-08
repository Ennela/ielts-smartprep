package com.smartprep.service;

import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.WritingFullResultResponse;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ExamAttempt;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingFullSubmission;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingFullSubmissionRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The short transactions that bracket writing evaluation.
 *
 * <p>Grading an essay means calling Gemini, which is allowed 65 seconds per attempt with up
 * to three attempts. When that call sat inside the same transaction as the writes around it,
 * a pooled database connection was held for the whole round trip; full-test submission made
 * two such calls under one transaction.
 *
 * <p>These live on their own bean rather than as private methods on {@link WritingService}
 * because {@code @Transactional} is applied by a proxy — a call between two methods of the
 * same bean does not pass through it, so the annotation would look right and do nothing.
 *
 * <p>What is deliberately preserved: each write below is still one transaction. A graded
 * essay and its score-history row commit together, and a full writing test commits both
 * submissions, the aggregate row and the score history together. Only the network call moved
 * out.
 */
@Service
@RequiredArgsConstructor
public class WritingGradingPersistence {

    private final UserRepository userRepository;
    private final WritingPromptRepository promptRepository;
    private final WritingSubmissionRepository submissionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final WritingFullSubmissionRepository writingFullSubmissionRepository;
    private final ExamAttemptService examAttemptService;
    private final WritingQueryService writingQueryService;

    /**
     * Persist one graded essay, optionally with a score-history entry.
     *
     * @param recordHistory {@code true} for a standalone Task submission, which records its
     *                      own band. Full-test submission passes {@code false} because the
     *                      history row it writes covers the combined score, and two rows for
     *                      one sitting would double-count in analytics.
     */
    @Transactional
    public WritingGradeResponse saveGradedEssay(Long userId,
                                                Long promptId,
                                                String essayText,
                                                int wordCount,
                                                GradingResult result,
                                                List<WritingGradeResponse.ErrorDto> errors,
                                                boolean recordHistory) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        WritingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found: " + promptId));

        WritingSubmission submission = submissionRepository.save(
                buildSubmission(user, prompt, essayText, wordCount, result));

        if (recordHistory) {
            scoreHistoryRepository.save(ScoreHistory.builder()
                    .user(user)
                    .skillType(SkillType.WRITING)
                    .score(result.getOverallBand())
                    .build());
        }

        return writingQueryService.buildGradeResponse(
                submission, prompt, errors, result.getImprovementNotes());
    }

    /**
     * Persist a whole full-writing sitting: both graded essays, the exam attempt's
     * completion, the aggregate row and one score-history entry — in a single transaction.
     *
     * <p>Everything from here on used to sit in {@code WritingAssemblyService} under one
     * {@code @Transactional} that also spanned both Gemini calls. The transaction is kept,
     * because a sitting that recorded Task 1 but not Task 2, or an aggregate row pointing at
     * a submission that was never written, would be genuinely inconsistent. Only the two
     * network calls moved out of it.
     */
    @Transactional
    public WritingFullResultResponse saveFullWriting(Long userId,
                                                     WritingSubmitFullRequest request,
                                                     int wordCount1,
                                                     int wordCount2,
                                                     GradingResult result1,
                                                     GradingResult result2,
                                                     List<WritingGradeResponse.ErrorDto> errors1,
                                                     List<WritingGradeResponse.ErrorDto> errors2) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        WritingPrompt prompt1 = promptRepository.findById(request.getTask1PromptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Writing prompt not found: " + request.getTask1PromptId()));
        WritingPrompt prompt2 = promptRepository.findById(request.getTask2PromptId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Writing prompt not found: " + request.getTask2PromptId()));

        WritingSubmission sub1 = submissionRepository.save(
                buildSubmission(user, prompt1, request.getTask1EssayText(), wordCount1, result1));
        WritingSubmission sub2 = submissionRepository.save(
                buildSubmission(user, prompt2, request.getTask2EssayText(), wordCount2, result2));

        // Task 2 counts double, matching the public band descriptors.
        BigDecimal weightedSum = result1.getOverallBand()
                .add(result2.getOverallBand().multiply(BigDecimal.valueOf(2)));
        BigDecimal average = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal overallWritingBand = IeltsScoringUtils.roundOverallBand(average);

        // Must happen before the aggregate row is built: it supplies timeSpentSeconds.
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

        WritingFullSubmission fullSub = writingFullSubmissionRepository.save(WritingFullSubmission.builder()
                .user(user)
                .task1Submission(sub1)
                .task2Submission(sub2)
                .overallBand(overallWritingBand)
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build());

        scoreHistoryRepository.save(ScoreHistory.builder()
                .user(user).skillType(SkillType.WRITING).score(overallWritingBand)
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build());

        return WritingFullResultResponse.builder()
                .id(fullSub.getId())
                .overallWritingBand(overallWritingBand)
                .task1Result(writingQueryService.buildGradeResponse(
                        sub1, prompt1, errors1, result1.getImprovementNotes()))
                .task2Result(writingQueryService.buildGradeResponse(
                        sub2, prompt2, errors2, result2.getImprovementNotes()))
                .submittedAt(fullSub.getSubmittedAt())
                .timeSpentSeconds(timeSpentSeconds)
                .timeSpentTask1(timeSpentTask1)
                .timeSpentTask2(timeSpentTask2)
                .autoSubmitted(autoSubmitted)
                .build();
    }

    private WritingSubmission buildSubmission(User user,
                                              WritingPrompt prompt,
                                              String essayText,
                                              int wordCount,
                                              GradingResult result) {
        return WritingSubmission.builder()
                .user(user)
                .prompt(prompt)
                .essayText(essayText)
                .wordCount(wordCount)
                .overallBand(result.getOverallBand())
                .taskResponseScore(result.getTaskResponse())
                .coherenceScore(result.getCoherence())
                .lexicalScore(result.getLexical())
                .grammarScore(result.getGrammar())
                .errorListJson(result.getErrorsJson())
                .rewrittenVersion(result.getRewrittenEssay())
                .aiFeedback(result.getGeneralFeedback())
                .build();
    }
}
