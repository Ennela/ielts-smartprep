package com.smartprep.service.ai;

import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.MockTestService;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The two short database transactions that bracket mock-test writing evaluation.
 *
 * <p>This exists as its own bean rather than as private methods on {@link MockTestAsyncGrader}
 * because Spring's {@code @Transactional} is applied by a proxy. A call from one method of a
 * bean to another method of the same bean does not pass through that proxy, so an annotation
 * there would be silently ignored -- the boundaries would look right in the source and do
 * nothing at runtime.
 *
 * <p>Why the split matters: grading calls Gemini twice, each with a 65-second timeout and up
 * to three retries with exponential backoff. Held inside one transaction, as it previously
 * was, a single submission could pin a pooled database connection for minutes. Ten
 * concurrent submissions -- the async executor's maximum -- would hold ten connections,
 * which is the entire default Hikari pool, and every other request in the application would
 * then block waiting for a connection that only a Gemini response could release.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestGradingPersistence {

    private final MockTestSubmissionRepository submissionRepository;
    private final WritingSubmissionRepository writingSubmissionRepository;
    private final WritingPromptRepository writingPromptRepository;
    private final UserRepository userRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;

    /**
     * Everything the Gemini calls need, read out of the database and detached from it.
     *
     * <p>Plain values, deliberately: the entities behind them use lazy associations, so
     * passing entities out of the transaction would only move the lazy load to a point where
     * no session exists and turn it into a {@code LazyInitializationException}.
     */
    @Getter
    @Builder
    public static class GradingInputs {
        private final Long userId;
        private final Long task1PromptId;
        private final Long task2PromptId;
        private final String task1PromptText;
        private final String task2PromptText;
    }

    /**
     * First transaction: resolve the lazy associations and copy out what grading needs.
     */
    @Transactional(readOnly = true)
    public GradingInputs loadInputs(Long submissionId) {
        MockTestSubmission submission = submissionRepository.findById(submissionId)
                .orElse(null);
        if (submission == null) {
            return null;
        }

        MockTest mockTest = submission.getMockTest();

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

        return GradingInputs.builder()
                .userId(submission.getUser().getUserId())
                .task1PromptId(task1Prompt.getPromptId())
                .task2PromptId(task2Prompt.getPromptId())
                .task1PromptText(task1Prompt.getPromptText())
                .task2PromptText(task2Prompt.getPromptText())
                .build();
    }

    /**
     * Second transaction: write the two essays, the aggregate scores and the final status.
     *
     * <p>Still one transaction, so a submission can never be left pointing at one graded
     * essay and not the other. It just no longer contains a network call.
     */
    @Transactional
    public void persistResults(Long submissionId,
                               GradingInputs inputs,
                               String task1Essay,
                               String task2Essay,
                               WritingGradingService.GradingResult task1Result,
                               WritingGradingService.GradingResult task2Result) {

        MockTestSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "MockTestSubmission " + submissionId + " disappeared while it was being graded."));

        User user = userRepository.getReferenceById(inputs.getUserId());
        WritingPrompt task1Prompt = writingPromptRepository.getReferenceById(inputs.getTask1PromptId());
        WritingPrompt task2Prompt = writingPromptRepository.getReferenceById(inputs.getTask2PromptId());

        WritingSubmission ws1 = writingSubmissionRepository.save(
                buildSubmission(user, task1Prompt, task1Essay, task1Result));
        WritingSubmission ws2 = writingSubmissionRepository.save(
                buildSubmission(user, task2Prompt, task2Essay, task2Result));

        // Writing aggregate: Task 2 counts double, matching the public band descriptors.
        BigDecimal weightedSum = task1Result.getOverallBand()
                .add(task2Result.getOverallBand().multiply(BigDecimal.valueOf(2)));
        BigDecimal writingAverage = weightedSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal roundedWritingScore = IeltsScoringUtils.roundOverallBand(writingAverage);

        BigDecimal overallSum = submission.getListeningScore()
                .add(submission.getReadingScore())
                .add(roundedWritingScore);
        BigDecimal overallAverage = overallSum.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        BigDecimal roundedOverallBand = IeltsScoringUtils.roundOverallBand(overallAverage);

        submission.setWritingTask1Submission(ws1);
        submission.setWritingTask2Submission(ws2);
        submission.setWritingScore(roundedWritingScore);
        submission.setOverallBand(roundedOverallBand);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submissionRepository.save(submission);

        // The writing band joins the sitting's Listening and Reading rows in score_history
        // (written at submit time by MockTestService). Guarded, because this method can run
        // more than once for one submission -- regradeWriting re-dispatches a FAILED or
        // stalled grade -- and a second row would count the sitting twice in every average.
        if (!scoreHistoryRepository.existsByMockTestSubmissionSubmissionIdAndSkillType(
                submissionId, SkillType.WRITING)) {
            scoreHistoryRepository.save(ScoreHistory.builder()
                    .user(user)
                    .skillType(SkillType.WRITING)
                    .score(roundedWritingScore)
                    .difficulty(MockTestService.MOCK_TEST_DIFFICULTY)
                    .mockTestSubmission(submission)
                    .build());
        }

        log.info("Asynchronous grading successfully completed for MockTestSubmission ID: {}. Overall Band: {}",
                submissionId, roundedOverallBand);
    }

    /**
     * Third, and only on the error path: record that grading failed so the user is not left
     * watching a spinner, and so {@code MockTestService.regradeWriting} has something to retry.
     */
    @Transactional
    public void markFailed(Long submissionId) {
        submissionRepository.findById(submissionId).ifPresent(submission -> {
            submission.setStatus(SubmissionStatus.FAILED);
            submissionRepository.save(submission);
        });
    }

    private WritingSubmission buildSubmission(User user,
                                              WritingPrompt prompt,
                                              String essay,
                                              WritingGradingService.GradingResult result) {
        return WritingSubmission.builder()
                .user(user)
                .prompt(prompt)
                .essayText(essay)
                .wordCount(result.getWordCount())
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
