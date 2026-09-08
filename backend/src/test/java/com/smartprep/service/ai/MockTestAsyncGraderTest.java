package com.smartprep.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MockTestAsyncGrader}.
 *
 * <p>The point of these is the transaction boundary, not the arithmetic. Grading calls Gemini
 * twice, each with a 65-second timeout and up to three retries, and this used to happen inside
 * one {@code @Transactional} method -- so a pooled database connection was held for the whole
 * network round trip, and ten concurrent submissions could take the entire Hikari pool with
 * them and stall the application.
 */
@ExtendWith(MockitoExtension.class)
class MockTestAsyncGraderTest {

    @Mock
    private WritingGradingService gradingService;

    @Mock
    private MockTestGradingPersistence persistence;

    @InjectMocks
    private MockTestAsyncGrader grader;

    private static MockTestGradingPersistence.GradingInputs inputs() {
        return MockTestGradingPersistence.GradingInputs.builder()
                .userId(1L)
                .task1PromptId(10L)
                .task2PromptId(20L)
                .task1PromptText("Describe the chart.")
                .task2PromptText("Do you agree?")
                .build();
    }

    private static WritingGradingService.GradingResult result(String band) {
        WritingGradingService.GradingResult r = new WritingGradingService.GradingResult();
        r.setOverallBand(new BigDecimal(band));
        return r;
    }

    @Test
    @DisplayName("reads its inputs, then calls Gemini, then persists — never all in one step")
    void bracketsTheAiCallsBetweenTwoSeparateDatabaseSteps() {
        when(persistence.loadInputs(5L)).thenReturn(inputs());
        when(gradingService.evaluateEssay(anyString(), anyString(), anyBoolean()))
                .thenReturn(result("7.0"));

        grader.gradeWritingSubmissionsAsync(5L, "task one essay", "task two essay");

        // The ordering is the invariant. Reading happens and finishes before the first Gemini
        // call; writing happens only after the second returns. Nothing spans them.
        InOrder inOrder = inOrder(persistence, gradingService);
        inOrder.verify(persistence).loadInputs(5L);
        inOrder.verify(gradingService).evaluateEssay("Describe the chart.", "task one essay", true);
        inOrder.verify(gradingService).evaluateEssay("Do you agree?", "task two essay", false);
        inOrder.verify(persistence).persistResults(
                eq(5L), any(), eq("task one essay"), eq("task two essay"), any(), any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("does not hold a transaction across the Gemini calls")
    void theAsyncMethodIsNotItselfTransactional() throws Exception {
        Method method = MockTestAsyncGrader.class.getMethod(
                "gradeWritingSubmissionsAsync", Long.class, String.class, String.class);

        // A regression guard with a specific target: re-adding @Transactional here would
        // reinstate exactly the behaviour this class was restructured to remove, and it would
        // do so silently -- everything would still pass, just slowly and under load-bearing
        // connection starvation.
        assertThat(method.getAnnotation(Transactional.class))
                .as("@Transactional on the async grader would hold a DB connection for the "
                        + "entire Gemini round trip; the transactions belong on "
                        + "MockTestGradingPersistence")
                .isNull();
        assertThat(MockTestAsyncGrader.class.getAnnotation(Transactional.class))
                .as("a class-level @Transactional would have the same effect")
                .isNull();
    }

    @Test
    @DisplayName("the persistence steps are the things that are transactional")
    void theShortDatabaseStepsCarryTheTransactions() throws Exception {
        Transactional load = MockTestGradingPersistence.class
                .getMethod("loadInputs", Long.class).getAnnotation(Transactional.class);
        Transactional persist = MockTestGradingPersistence.class
                .getMethod("persistResults", Long.class,
                        MockTestGradingPersistence.GradingInputs.class, String.class, String.class,
                        WritingGradingService.GradingResult.class,
                        WritingGradingService.GradingResult.class)
                .getAnnotation(Transactional.class);

        assertThat(load).isNotNull();
        assertThat(load.readOnly()).isTrue();
        assertThat(persist).isNotNull();
        // Both essays and the submission status still commit together, so a submission can
        // never be left holding one graded essay and not the other.
        assertThat(persist.readOnly()).isFalse();
    }

    @Test
    @DisplayName("a Gemini failure marks the submission FAILED instead of leaving it grading")
    void marksFailedWhenGradingThrows() {
        when(persistence.loadInputs(7L)).thenReturn(inputs());
        when(gradingService.evaluateEssay(anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("Gemini timed out"));

        grader.gradeWritingSubmissionsAsync(7L, "one", "two");

        verify(persistence).markFailed(7L);
        verify(persistence, never()).persistResults(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a submission that no longer exists is abandoned, not marked failed")
    void abandonsAnUnknownSubmission() {
        when(persistence.loadInputs(404L)).thenReturn(null);

        grader.gradeWritingSubmissionsAsync(404L, "one", "two");

        verifyNoInteractions(gradingService);
        // Nothing to mark: there is no row to carry the status.
        verify(persistence, never()).markFailed(anyLong());
        verify(persistence, never()).persistResults(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a failure while reading inputs also marks the submission FAILED")
    void marksFailedWhenLoadingInputsThrows() {
        when(persistence.loadInputs(9L)).thenThrow(new IllegalStateException("no writing prompts"));

        grader.gradeWritingSubmissionsAsync(9L, "one", "two");

        verifyNoInteractions(gradingService);
        verify(persistence).markFailed(9L);
    }
}
