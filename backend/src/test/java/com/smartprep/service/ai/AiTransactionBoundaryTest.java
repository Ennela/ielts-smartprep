package com.smartprep.service.ai;

import com.smartprep.service.ReviewService;
import com.smartprep.service.WritingAssemblyService;
import com.smartprep.service.WritingGradingPersistence;
import com.smartprep.service.WritingService;
import com.smartprep.service.vocab.VocabularyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one invariant that the whole AI-in-transaction fix rests on: no method that
 * calls Gemini may be {@code @Transactional}.
 *
 * <p>Gemini is allowed 65 seconds per attempt with up to three attempts, so a transaction
 * around one of these calls holds a pooled database connection for that entire time. With a
 * pool of 20 and two async executors that can want 15 connections between them, enough
 * concurrent generation exhausts the pool and blocks every unrelated request in the
 * application — a denial of service reachable through ordinary use of the product.
 *
 * <p>This is asserted reflectively rather than by behaviour because the failure is silent.
 * Re-adding {@code @Transactional} to any of these breaks nothing visible: every test still
 * passes, every response is still correct, and the damage only appears under concurrency in
 * production. A reviewer would have to know the history to catch it. This test does.
 *
 * <p>The companion property — that a method without the annotation really does give its
 * connection back rather than holding one for the whole request — is asserted separately by
 * {@code ConnectionHoldingIntegrationTest}, which needs a real database.
 */
class AiTransactionBoundaryTest {

    /**
     * Every method that reaches Gemini, directly or through a collaborator that does.
     * Recorded by name because that is what a future edit would change.
     */
    private static final String[][] AI_CALLING_METHODS = {
            {ReadingGenerationService.class.getName(), "generateQuiz"},
            {WritingGenerationService.class.getName(), "generatePromptPair"},
            {ListeningGenerationService.class.getName(), "generateFullTest"},
            {ListeningGenerationService.class.getName(), "generatePart"},
            {ListeningGenerationService.class.getName(), "analyzeQuestion"},
            {ListeningGenerationService.class.getName(), "extractVocabulary"},
            {MockTestAsyncGrader.class.getName(), "gradeWritingSubmissionsAsync"},
            {WritingService.class.getName(), "gradeEssay"},
            {WritingService.class.getName(), "gradeOnly"},
            {WritingAssemblyService.class.getName(), "submitFullWriting"},
            {ReviewService.class.getName(), "explainAnswer"},
            {VocabularyService.class.getName(), "suggestVocabulary"},
    };

    @Test
    @DisplayName("no method that calls Gemini is transactional")
    void noAiCallingMethodHoldsATransaction() throws Exception {
        List<String> offenders = new ArrayList<>();

        for (String[] entry : AI_CALLING_METHODS) {
            Class<?> type = Class.forName(entry[0]);
            String methodName = entry[1];

            // A class-level annotation would cover every overload at once.
            if (type.getAnnotation(Transactional.class) != null) {
                offenders.add(type.getSimpleName() + " (class-level @Transactional)");
            }

            List<Method> matches = new ArrayList<>();
            for (Method m : type.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    matches.add(m);
                }
            }
            assertThat(matches)
                    .as("%s.%s no longer exists — if it was renamed, update this list rather "
                            + "than deleting the entry", type.getSimpleName(), methodName)
                    .isNotEmpty();

            for (Method m : matches) {
                if (m.getAnnotation(Transactional.class) != null) {
                    offenders.add(type.getSimpleName() + "." + methodName + "(" + m.getParameterCount() + " args)");
                }
            }
        }

        assertThat(offenders)
                .as("these call Gemini inside a transaction, so each holds a pooled database "
                        + "connection for the whole AI round trip; move the database work into "
                        + "a persistence bean and leave the AI call outside it")
                .isEmpty();
    }

    @Test
    @DisplayName("the writes those methods delegate are still transactional")
    void thePersistenceBeansStillCarryTheTransactions() throws Exception {
        // The other half of the invariant. Removing @Transactional from the AI methods is
        // only correct because the writes moved somewhere that still has one -- otherwise
        // this would be a fix for connection holding that quietly gave up atomicity.
        assertMethodIsTransactional(WritingGradingPersistence.class, "saveGradedEssay");
        assertMethodIsTransactional(WritingGradingPersistence.class, "saveFullWriting");
        assertMethodIsTransactional(WritingPromptPersistence.class, "savePair");
        assertMethodIsTransactional(MockTestGradingPersistence.class, "persistResults");
        assertMethodIsTransactional(MockTestGradingPersistence.class, "loadInputs");
    }

    private static void assertMethodIsTransactional(Class<?> type, String methodName) {
        boolean found = false;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                found = true;
                assertThat(m.getAnnotation(Transactional.class))
                        .as("%s.%s must stay transactional: it is where the writes that used "
                                + "to be atomic now commit", type.getSimpleName(), methodName)
                        .isNotNull();
            }
        }
        assertThat(found)
                .as("%s.%s no longer exists", type.getSimpleName(), methodName)
                .isTrue();
    }
}
