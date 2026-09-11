package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.AnalyticsThresholdConfig;
import com.smartprep.config.CacheConfig;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.service.MockTestAnalyticsCalculator.QuestionAnalytics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The caching contract around {@link MockTestAnalyticsCalculator}, exercised through a
 * real Spring cache proxy (in-memory, so no Redis is needed) rather than by reasoning about
 * annotations.
 *
 * <p>Three things must hold. A repeat request for the same submission in the same status
 * must not touch the database again. A status change must be a different entry, so a
 * submission that finishes grading is never served its GRADING-era analytics. And the
 * value must survive the JSON round trip the Redis serializer puts it through -- a
 * missing no-args constructor there only fails at runtime, on the first cache hit.
 */
@SpringJUnitConfig(MockTestAnalyticsCacheTest.Config.class)
class MockTestAnalyticsCacheTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager(MockTestAnalyticsCalculator.CACHE_NAME); }
        @Bean MockTestSubmissionRepository submissionRepository() { return mock(MockTestSubmissionRepository.class); }
        @Bean MockTestSessionRepository sessionRepository() { return mock(MockTestSessionRepository.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AnalyticsThresholdConfig thresholds() {
            AnalyticsThresholdConfig t = new AnalyticsThresholdConfig();
            ReflectionTestUtils.setField(t, "accuracyWeakBelow", 60.0);
            ReflectionTestUtils.setField(t, "accuracyStrongFrom", 80.0);
            ReflectionTestUtils.setField(t, "bandWeakBelow", new BigDecimal("5.5"));
            ReflectionTestUtils.setField(t, "bandStrongFrom", new BigDecimal("7.0"));
            ReflectionTestUtils.setField(t, "minSampleSize", 3);
            return t;
        }
        @Bean MockTestAnalyticsCalculator calculator(MockTestSubmissionRepository s, MockTestSessionRepository ss,
                                                      ObjectMapper om, AnalyticsThresholdConfig t) {
            return new MockTestAnalyticsCalculator(s, ss, om, t);
        }
    }

    @Autowired private MockTestAnalyticsCalculator calculator;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private CacheManager cacheManager;

    private static final Long SUBMISSION_ID = 42L;

    private MockTestSubmission submission(SubmissionStatus status) {
        MockTest paper = MockTest.builder()
                .mockTestId(1L)
                .listeningParts(List.of(ListeningPart.builder().partId(1L).partNumber(1).questions(List.of(
                        ListeningQuestion.builder().questionId(11L).orderIndex(1).questionType(QuestionType.MCQ)
                                .questionText("Q1").correctAnswer("A").build())).build()))
                .readingQuizzes(List.of(ReadingQuiz.builder().quizId(1L).questions(List.of(
                        ReadingQuestion.builder().questionId(21L).orderIndex(1).questionType(QuestionType.TFNG)
                                .questionText("Q1").correctAnswer("TRUE").build())).build()))
                .build();
        WritingSubmission essay = WritingSubmission.builder().submissionId(7L).wordCount(250)
                .overallBand(new BigDecimal("6.0")).taskResponseScore(new BigDecimal("6.0"))
                .coherenceScore(new BigDecimal("6.0")).lexicalScore(new BigDecimal("6.0"))
                .grammarScore(new BigDecimal("6.0")).build();
        return MockTestSubmission.builder()
                .submissionId(SUBMISSION_ID).sessionId(9L).mockTest(paper).status(status)
                .writingScore(new BigDecimal("6.0"))
                .writingTask1Submission(status == SubmissionStatus.COMPLETED ? essay : null)
                .writingTask2Submission(status == SubmissionStatus.COMPLETED ? essay : null)
                .build();
    }

    private void freshStubs(SubmissionStatus status) {
        reset(submissionRepository, sessionRepository);
        cacheManager.getCache(MockTestAnalyticsCalculator.CACHE_NAME).clear();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission(status)));
        when(sessionRepository.findById(9L)).thenReturn(Optional.of(
                MockTestSession.builder().sessionId(9L).progressJson("{\"11\":\"A\",\"21\":\"FALSE\"}").build()));
    }

    @Test
    @DisplayName("a second request for the same submission and status is served from cache")
    void repeatRequestHitsCache() {
        freshStubs(SubmissionStatus.COMPLETED);

        QuestionAnalytics first = calculator.compute(SUBMISSION_ID, SubmissionStatus.COMPLETED);
        QuestionAnalytics second = calculator.compute(SUBMISSION_ID, SubmissionStatus.COMPLETED);

        assertEquals(first, second);
        assertEquals(1, first.getListening().getCorrect());
        assertEquals(0, first.getReading().getCorrect());
        verify(submissionRepository, times(1)).findById(SUBMISSION_ID);
        verify(sessionRepository, times(1)).findById(9L);
    }

    @Test
    @DisplayName("a status change is a different entry, so finished grading is never served stale")
    void statusIsPartOfTheKey() {
        freshStubs(SubmissionStatus.GRADING);
        QuestionAnalytics grading = calculator.compute(SUBMISSION_ID, SubmissionStatus.GRADING);
        assertNull(grading.getWriting());

        // Grading lands: the row now says COMPLETED and carries essays.
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission(SubmissionStatus.COMPLETED)));
        QuestionAnalytics completed = calculator.compute(SUBMISSION_ID, SubmissionStatus.COMPLETED);

        assertNotNull(completed.getWriting());
        assertEquals(new BigDecimal("6.0"), completed.getWriting().getBand());
        verify(submissionRepository, times(2)).findById(SUBMISSION_ID);
    }

    @Test
    @DisplayName("the cached value survives the Redis JSON round trip")
    void valueRoundTripsThroughRedisSerializer() {
        freshStubs(SubmissionStatus.COMPLETED);
        QuestionAnalytics original = calculator.compute(SUBMISSION_ID, SubmissionStatus.COMPLETED);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        Object restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(original, restored);
    }

    @Test
    @DisplayName("a cache backend failure is logged and the value is computed, not turned into an error")
    void cacheFailureDegradesToCompute() {
        CacheErrorHandler handler = new CacheConfig().errorHandler();
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(MockTestAnalyticsCalculator.CACHE_NAME);
        RuntimeException redisDown = new RuntimeException("Connection refused");

        assertDoesNotThrow(() -> handler.handleCacheGetError(redisDown, cache, "42:COMPLETED"));
        assertDoesNotThrow(() -> handler.handleCachePutError(redisDown, cache, "42:COMPLETED", new Object()));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(redisDown, cache, "42:COMPLETED"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(redisDown, cache));
    }
}
