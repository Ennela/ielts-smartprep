package com.smartprep.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestAsyncGrader {

    private final WritingGradingService gradingService;
    private final MockTestGradingPersistence persistence;

    /**
     * Grade mock test essays asynchronously.
     *
     * <p>Deliberately not {@code @Transactional}. The two Gemini calls below each allow a
     * 65-second timeout and up to three retries with exponential backoff, so a single
     * submission can spend minutes waiting on the network. When this method held a
     * transaction, it held a pooled database connection for that entire time; ten
     * submissions grading at once -- the async executor's maximum -- exhausted the whole
     * connection pool, and every unrelated request in the application blocked behind them.
     *
     * <p>So the database work is bracketed instead: read what grading needs and commit,
     * call Gemini with no connection held, then persist the results in a second short
     * transaction. Those transactions live on {@link MockTestGradingPersistence} because
     * a self-invocation would bypass the transactional proxy entirely.
     *
     * <p>The result-writing step is still a single transaction, so a submission is never
     * left holding one graded essay and not the other.
     */
    @Async("taskExecutor")
    public void gradeWritingSubmissionsAsync(Long submissionId, String task1Essay, String task2Essay) {
        log.info("Starting asynchronous grading for MockTestSubmission ID: {}", submissionId);

        MockTestGradingPersistence.GradingInputs inputs;
        try {
            inputs = persistence.loadInputs(submissionId);
        } catch (Exception e) {
            log.error("Could not read the inputs for MockTestSubmission ID: {}", submissionId, e);
            persistence.markFailed(submissionId);
            return;
        }

        if (inputs == null) {
            log.error("MockTestSubmission with ID {} not found. Aborting async grading.", submissionId);
            return;
        }

        try {
            // No transaction is open across these two calls, which is the entire point.
            log.info("Grading Writing Task 1 for submission ID: {}", submissionId);
            WritingGradingService.GradingResult task1Result =
                    gradingService.evaluateEssay(inputs.getTask1PromptText(), task1Essay, true);

            log.info("Grading Writing Task 2 for submission ID: {}", submissionId);
            WritingGradingService.GradingResult task2Result =
                    gradingService.evaluateEssay(inputs.getTask2PromptText(), task2Essay, false);

            persistence.persistResults(
                    submissionId, inputs, task1Essay, task2Essay, task1Result, task2Result);

        } catch (Exception e) {
            log.error("Error occurred during async grading for MockTestSubmission ID: {}", submissionId, e);
            persistence.markFailed(submissionId);
        }
    }
}
