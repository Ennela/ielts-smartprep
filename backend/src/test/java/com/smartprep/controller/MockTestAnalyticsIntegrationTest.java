package com.smartprep.controller;

import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.*;
import com.smartprep.service.LoginLockoutService;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The analytics endpoint end to end: real Spring context, real schema, real seeded mock
 * test, JSON out over MockMvc.
 *
 * <p>What the unit tests cannot see and this can: that the threshold bean binds from
 * {@code app.analytics.*}, that the response serialises, that lazy associations resolve
 * inside the read-only transaction, and that the security chain returns 404 -- not 403,
 * not 500 -- for a submission that belongs to someone else.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
// Each test seeds its own sitting and is rolled back afterwards: the shared container
// stays clean for the other integration tests, and no unique-key collision between runs.
@Transactional
class MockTestAnalyticsIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private WritingSubmissionRepository writingSubmissionRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CacheManager cacheManager;

    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    private User owner;
    private User stranger;
    private Long submissionId;
    private int listeningQuestions;
    private int readingQuestions;
    private List<String> partKeys;

    @BeforeEach
    void seedOneCompletedSitting() {
        owner = userRepository.save(User.builder()
                .username("analytics_owner").passwordHash("x").email("analytics_owner@example.test")
                .displayName("Owner").role(Role.STUDENT)
                .targetReadingScore(new BigDecimal("7.0")).build());
        stranger = userRepository.save(User.builder()
                .username("analytics_stranger").passwordHash("x").email("analytics_stranger@example.test")
                .displayName("Stranger").role(Role.STUDENT).build());

        // The Cambridge 19 paper: seeded with four listening parts AND three reading
        // passages, unlike the original sample test which has no reading section.
        MockTest test = mockTestRepository.findAll().stream()
                .filter(m -> "Cambridge IELTS 19 Test 1".equals(m.getTitle()))
                .findFirst().orElseThrow();
        List<WritingPrompt> prompts = writingPromptRepository.findAll();

        // Expected totals come from the seeded content itself; read inside a transaction
        // because the collections are lazy.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            MockTest attached = mockTestRepository.findById(test.getMockTestId()).orElseThrow();
            listeningQuestions = attached.getListeningParts().stream()
                    .mapToInt(p -> p.getQuestions().size()).sum();
            readingQuestions = attached.getReadingQuizzes().stream()
                    .mapToInt(q -> q.getQuestions().size()).sum();
            partKeys = attached.getListeningParts().stream()
                    .map(p -> "Part " + p.getPartNumber()).collect(Collectors.toList());
        });

        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(owner).mockTest(test).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0)
                .progressJson("{\"w_task1\":\"essay one\",\"w_task2\":\"essay two\"}")
                .build());

        WritingSubmission task1 = writingSubmissionRepository.save(WritingSubmission.builder()
                .user(owner).prompt(prompts.get(0)).essayText("essay one").wordCount(160)
                .overallBand(new BigDecimal("5.0")).taskResponseScore(new BigDecimal("5.0"))
                .coherenceScore(new BigDecimal("5.0")).lexicalScore(new BigDecimal("5.0"))
                .grammarScore(new BigDecimal("5.0")).build());
        WritingSubmission task2 = writingSubmissionRepository.save(WritingSubmission.builder()
                .user(owner).prompt(prompts.get(prompts.size() > 1 ? 1 : 0)).essayText("essay two").wordCount(260)
                .overallBand(new BigDecimal("6.0")).taskResponseScore(new BigDecimal("6.0"))
                .coherenceScore(new BigDecimal("7.0")).lexicalScore(new BigDecimal("6.0"))
                .grammarScore(new BigDecimal("5.0")).build());

        submissionId = submissionRepository.save(MockTestSubmission.builder()
                .user(owner).mockTest(test).sessionId(session.getSessionId())
                .status(SubmissionStatus.COMPLETED)
                .listeningScore(new BigDecimal("6.0")).readingScore(new BigDecimal("5.5"))
                .writingScore(new BigDecimal("5.5")).overallBand(new BigDecimal("5.5"))
                .listeningCorrectAnswers(0).readingCorrectAnswers(0)
                .writingTask1Submission(task1).writingTask2Submission(task2)
                .build()).getSubmissionId();
    }

    private static RequestPostProcessor loggedInAs(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    @Test
    @DisplayName("the owner gets bands, question-level breakdown, weaknesses, progress and next steps")
    void ownerGetsAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/mock-tests/submissions/{id}/analytics", submissionId)
                        .with(loggedInAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.summary.overallBand").value(5.5))
                .andExpect(jsonPath("$.data.summary.speakingIncluded").value(false))
                .andExpect(jsonPath("$.data.summary.speakingNote").value("Speaking: Not included"))
                .andExpect(jsonPath("$.data.summary.weakestSkills", contains("READING", "WRITING")))
                .andExpect(jsonPath("$.data.skills", hasSize(3)))
                .andExpect(jsonPath("$.data.skills[1].skill").value("READING"))
                .andExpect(jsonPath("$.data.skills[1].targetBand").value(7.0))
                .andExpect(jsonPath("$.data.skills[1].gapToTarget").value(1.5))
                // Nothing was answered, so every question is wrong and every group is weak.
                .andExpect(jsonPath("$.data.listening.total").value(listeningQuestions))
                .andExpect(jsonPath("$.data.listening.correct").value(0))
                .andExpect(jsonPath("$.data.listening.byPart[*].key", contains(partKeys.toArray())))
                .andExpect(jsonPath("$.data.reading.total").value(readingQuestions))
                .andExpect(jsonPath("$.data.reading.wrongQuestions", hasSize(readingQuestions)))
                .andExpect(jsonPath("$.data.reading.byQuestionType[0].level").value("WEAK"))
                .andExpect(jsonPath("$.data.writing.criteria", hasSize(4)))
                .andExpect(jsonPath("$.data.writing.criteria[3].key").value("GRAMMAR"))
                .andExpect(jsonPath("$.data.writing.criteria[3].band").value(5.0))
                .andExpect(jsonPath("$.data.writing.criteria[3].level").value("WEAK"))
                .andExpect(jsonPath("$.data.weaknesses", not(empty())))
                .andExpect(jsonPath("$.data.weaknesses[0].level").value("WEAK"))
                .andExpect(jsonPath("$.data.progress.attemptNumber").value(1))
                .andExpect(jsonPath("$.data.progress.totalAttempts").value(1))
                .andExpect(jsonPath("$.data.progress.timeline[0].current").value(true))
                .andExpect(jsonPath("$.data.progress.trends[0].direction").value("FIRST_ATTEMPT"))
                .andExpect(jsonPath("$.data.recommendations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.recommendations[0].actionPath", startsWith("/")));
    }

    @Test
    @DisplayName("someone else's submission is 404, indistinguishable from a missing one")
    void strangerGetsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/mock-tests/submissions/{id}/analytics", submissionId)
                        .with(loggedInAs(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/mock-tests/submissions/{id}/analytics", 999_999L)
                        .with(loggedInAs(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the test profile's cache.type=none is honoured, so no Redis is needed here or in CI")
    void testProfileRunsWithoutRedis() {
        // CacheConfig's Redis manager used to be unconditional and silently won over this
        // setting; the analytics endpoint would then have needed a live Redis in every test.
        assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
    }

    @Test
    @DisplayName("unauthenticated requests are refused")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/mock-tests/submissions/{id}/analytics", submissionId))
                .andExpect(status().is4xxClientError());
    }
}
