package com.smartprep.controller;

import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.service.LoginLockoutService;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.MockTestAsyncGrader;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The property the AI entry points depend on, measured where it matters: inside a real web
 * request, with {@code spring.jpa.open-in-view} keeping the EntityManager open for its
 * whole duration.
 *
 * <p>{@link com.smartprep.repository.ConnectionHoldingIntegrationTest} shows that a
 * non-transactional read releases its connection at once -- but it runs as a
 * {@code @DataJpaTest}, with no request and no OpenEntityManagerInViewInterceptor, so its
 * session ends when the read does. In the application the session lives until the
 * response is written, and Spring's HibernateJpaVendorAdapter sets Hibernate's handling
 * mode to DELAYED_ACQUISITION_AND_HOLD -- release on session close. So the read-only
 * transaction behind {@code submitFullWriting}'s first {@code findById} checked out a
 * pooled connection that stayed out across both Gemini calls, and HikariCP's leak
 * detector said so on a real sitting. application.yml now sets
 * DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION; this class asserts the effect.
 *
 * <p>Not {@code @Transactional}: a transactional test would itself hold the connection
 * it is trying to prove nobody holds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AiCallConnectionHoldingIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private UserRepository userRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private GeminiClient geminiClient;
    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    private User user;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .username("pool_watcher").passwordHash("x").email("pool_watcher@example.test")
                .displayName("Pool").role(Role.STUDENT).build());
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteById(user.getUserId());
    }

    private HikariPoolMXBean pool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    private RequestPostProcessor asUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Test
    @DisplayName("no pooled connection is checked out while the request is inside the Gemini call")
    void noConnectionHeldAcrossTheAiCall() throws Exception {
        List<WritingPrompt> prompts = writingPromptRepository.findAll();
        assertThat(prompts).hasSizeGreaterThanOrEqualTo(2);

        CountDownLatch insideGemini = new CountDownLatch(1);
        CountDownLatch letGeminiReturn = new CountDownLatch(1);
        when(geminiClient.gradeAndParse(any(), any(), any())).thenAnswer(invocation -> {
            insideGemini.countDown();
            letGeminiReturn.await(30, TimeUnit.SECONDS);
            throw new IllegalStateException("stub: grading not simulated");
        });

        String body = """
                {"task1PromptId": %d, "task2PromptId": %d,
                 "task1EssayText": "%s", "task2EssayText": "%s"}
                """.formatted(prompts.get(0).getPromptId(), prompts.get(1).getPromptId(),
                "word ".repeat(160).trim(), "word ".repeat(260).trim());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> request = executor.submit(() -> mockMvc.perform(
                    post("/api/v1/writing/submit-full").with(asUser())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn());

            assertThat(insideGemini.await(30, TimeUnit.SECONDS))
                    .as("the request reached the Gemini stub").isTrue();
            // The reads before the AI call have run and the request is now waiting on
            // Gemini. This is the whole assertion: nothing from the pool is checked out.
            int activeWhileGrading = pool().getActiveConnections();

            letGeminiReturn.countDown();
            request.get(60, TimeUnit.SECONDS);

            assertThat(activeWhileGrading)
                    .as("connections checked out of the pool while Gemini was being called")
                    .isZero();
        } finally {
            letGeminiReturn.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("a transaction still holds its connection: two writes and a failure roll back together")
    void transactionalWritesStillAtomic() {
        // The other half of the mode: inside a transaction the connection is still held to
        // the end. (Release-after-statement, the JTA-only mode, would hand the commit a
        // different pooled connection -- "Can't call commit when autocommit=true".)
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            userRepository.save(User.builder()
                    .username("atomic_a").passwordHash("x").email("atomic_a@example.test")
                    .displayName("A").role(Role.STUDENT).build());
            userRepository.flush();
            assertThat(pool().getActiveConnections()).as("held for the transaction").isEqualTo(1);
            userRepository.save(User.builder()
                    .username("atomic_b").passwordHash("x").email("atomic_b@example.test")
                    .displayName("B").role(Role.STUDENT).build());
            userRepository.flush();
            throw new IllegalStateException("roll both back");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(userRepository.existsByUsername("atomic_a")).isFalse();
        assertThat(userRepository.existsByUsername("atomic_b")).isFalse();
        assertThat(pool().getActiveConnections()).isZero();
    }
}
