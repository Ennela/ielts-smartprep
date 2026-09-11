package com.smartprep.service;

import com.smartprep.controller.AdminQuestionController;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.ListeningQuestionRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.MockTestAnalyticsCalculator.QuestionAnalytics;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analytics cache against a real Redis, through the real {@code CacheConfig}.
 *
 * <p>{@code MockTestAnalyticsCacheTest} proves the proxy contract with an in-memory cache;
 * what it cannot prove is that the Redis manager is built the way {@code CacheConfig} says
 * -- the JSON serializer accepting the value, the per-cache TTL actually applied, the key
 * landing under the expected name. Those only fail at runtime, on the first real hit, so
 * they are checked here against a throwaway Redis container.
 *
 * <p>Not {@code @Transactional}: the cached method runs in its own read-only transaction
 * and the point is to observe what reached Redis, not to roll anything back. Rows are
 * removed by hand at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MockTestAnalyticsRedisCacheIntegrationTest extends AbstractMySQLContainerTest {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureRedisCache(DynamicPropertyRegistry registry) {
        // The test profile says cache.type=none; this class alone switches it back on and
        // points it at the container.
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private MockTestAnalyticsCalculator calculator;
    @Autowired private CacheManager cacheManager;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private ListeningQuestionRepository listeningQuestionRepository;
    @Autowired private AdminQuestionController adminQuestionController;

    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    @Test
    @DisplayName("a computed value is written to Redis as JSON, read back equal, and expires within the analytics TTL")
    void valueLandsInRedisWithTtl() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);

        User user = userRepository.save(User.builder()
                .username("redis_cache_user").passwordHash("x").email("redis_cache@example.test")
                .displayName("Cache").role(Role.STUDENT).build());
        MockTest test = mockTestRepository.findAll().get(0);
        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(user).mockTest(test).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0)
                .progressJson("{}").build());
        MockTestSubmission sub = submissionRepository.save(MockTestSubmission.builder()
                .user(user).mockTest(test).sessionId(session.getSessionId())
                .status(SubmissionStatus.GRADING)
                .listeningScore(new BigDecimal("5.0")).readingScore(new BigDecimal("5.0"))
                .writingScore(BigDecimal.ZERO).overallBand(BigDecimal.ZERO)
                .listeningCorrectAnswers(0).readingCorrectAnswers(0).build());

        String key = sub.getSubmissionId() + ":" + SubmissionStatus.GRADING;
        String redisKey = MockTestAnalyticsCalculator.CACHE_NAME + "::" + key;
        try {
            QuestionAnalytics computed = calculator.compute(sub.getSubmissionId(), SubmissionStatus.GRADING);

            Cache cache = cacheManager.getCache(MockTestAnalyticsCalculator.CACHE_NAME);
            Cache.ValueWrapper wrapper = cache.get(key);
            assertThat(wrapper).as("entry written under the expected key").isNotNull();
            // Read back through the same JSON serializer the manager writes with.
            assertThat(wrapper.get()).isInstanceOf(QuestionAnalytics.class).isEqualTo(computed);

            Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .as("per-cache TTL applied, not the 24h default")
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(Duration.ofHours(1).toSeconds());

            QuestionAnalytics again = calculator.compute(sub.getSubmissionId(), SubmissionStatus.GRADING);
            assertThat(again).isEqualTo(computed);
        } finally {
            redisTemplate.delete(redisKey);
            submissionRepository.delete(sub);
            sessionRepository.delete(session);
            userRepository.delete(user);
        }
    }

    @Test
    @DisplayName("an admin edit to graded content drops the cached analytics")
    void adminContentEditEvictsCache() {
        User user = userRepository.save(User.builder()
                .username("redis_evict_user").passwordHash("x").email("redis_evict@example.test")
                .displayName("Evict").role(Role.STUDENT).build());
        MockTest test = mockTestRepository.findAll().get(0);
        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(user).mockTest(test).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0)
                .progressJson("{}").build());
        MockTestSubmission sub = submissionRepository.save(MockTestSubmission.builder()
                .user(user).mockTest(test).sessionId(session.getSessionId())
                .status(SubmissionStatus.GRADING)
                .listeningScore(new BigDecimal("5.0")).readingScore(new BigDecimal("5.0"))
                .writingScore(BigDecimal.ZERO).overallBand(BigDecimal.ZERO)
                .listeningCorrectAnswers(0).readingCorrectAnswers(0).build());

        String key = sub.getSubmissionId() + ":" + SubmissionStatus.GRADING;
        Cache cache = cacheManager.getCache(MockTestAnalyticsCalculator.CACHE_NAME);
        ListeningQuestion question = listeningQuestionRepository.findAll().get(0);
        boolean wasVerified = Boolean.TRUE.equals(question.getVerified());
        try {
            calculator.compute(sub.getSubmissionId(), SubmissionStatus.GRADING);
            assertThat(cache.get(key)).as("populated before the edit").isNotNull();

            // The cheapest of the four evicting write paths: verifying a question with no
            // body changes nothing but the flag, yet it is the path that can rewrite an
            // answer key, so it clears the cache like the others.
            adminQuestionController.verifyQuestion("listening", question.getQuestionId(), null);

            assertThat(cache.get(key)).as("dropped by the admin edit").isNull();
        } finally {
            question.setVerified(wasVerified);
            listeningQuestionRepository.save(question);
            redisTemplate.delete(MockTestAnalyticsCalculator.CACHE_NAME + "::" + key);
            submissionRepository.delete(sub);
            sessionRepository.delete(session);
            userRepository.delete(user);
        }
    }
}
