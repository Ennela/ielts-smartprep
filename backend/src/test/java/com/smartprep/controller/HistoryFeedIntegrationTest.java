package com.smartprep.controller;

import com.smartprep.model.entity.*;
import com.smartprep.model.enums.*;
import com.smartprep.repository.*;
import com.smartprep.service.LoginLockoutService;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/history over the real schema: one page merging the four skills, newest
 * first, with the History page's three filters applied on the server and other users'
 * rows never included.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class HistoryFeedIntegrationTest extends AbstractMySQLContainerTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 1, 10, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private WritingSubmissionRepository writingSubmissionRepository;
    @Autowired private ListeningTestRepository listeningTestRepository;
    @Autowired private ReadingQuizRepository readingQuizRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private ScoreHistoryRepository scoreHistoryRepository;

    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    private User user;
    private User other;

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .username("feed_user").passwordHash("x").email("feed_user@example.test")
                .displayName("Feed").role(Role.STUDENT).build());
        other = userRepository.save(User.builder()
                .username("feed_other").passwordHash("x").email("feed_other@example.test")
                .displayName("Other").role(Role.STUDENT).build());

        // Six sittings for the user, one per hour from T0, oldest first: the feed must come
        // back newest first regardless of which table a row lives in.
        reading(user, Topic.HEALTH, "5.0", T0);                       // 1
        listening(user, TestMode.PRACTICE, "6.0", T0.plusHours(1));   // 2
        writing(user, "6.5", T0.plusHours(2));                        // 3
        mock(user, SubmissionStatus.GRADING, null, T0.plusHours(3));  // 4
        reading(user, Topic.SCIENCE, "7.0", T0.plusHours(4));         // 5
        mock(user, SubmissionStatus.COMPLETED, "6.5", T0.plusHours(5)); // 6
        // score_history rows: one 2 s after the SCIENCE quiz (matched, carries the time), one
        // 3 s after the listening test (matched), and one a minute after the HEALTH quiz
        // (outside the 5 s window: not matched).
        scoreHistory(SkillType.READING, T0.plusHours(4).plusSeconds(2), 1500);
        scoreHistory(SkillType.LISTENING, T0.plusHours(1).plusSeconds(3), null);
        scoreHistory(SkillType.READING, T0.plusMinutes(1), 999);
        // A quiz that was never submitted, and one that was deleted, are not history.
        readingQuizRepository.save(ReadingQuiz.builder()
                .user(user).topic(Topic.HISTORY).difficulty(Difficulty.PASSAGE_1)
                .passageText("p").totalQuestions(5).build());
        ReadingQuiz deleted = reading(user, Topic.HISTORY, "4.0", T0.plusHours(6));
        deleted.setDeletedAt(LocalDateTime.now());
        readingQuizRepository.save(deleted);
        // Somebody else's, newest of all: must never appear.
        reading(other, Topic.ENVIRONMENT, "9.0", T0.plusHours(7));

        entityManager.flush();
    }

    private void scoreHistory(SkillType skill, LocalDateTime recordedAt, Integer timeSpent) {
        ScoreHistory row = scoreHistoryRepository.save(ScoreHistory.builder()
                .user(user).skillType(skill).score(new BigDecimal("6.0")).difficulty("PASSAGE_1")
                .timeSpentSeconds(timeSpent).build());
        entityManager.flush();
        jdbc.update("UPDATE score_history SET recorded_at = ? WHERE history_id = ?", recordedAt, row.getHistoryId());
    }

    private ReadingQuiz reading(User owner, Topic topic, String band, LocalDateTime at) {
        ReadingQuiz quiz = readingQuizRepository.save(ReadingQuiz.builder()
                .user(owner).topic(topic).difficulty(Difficulty.PASSAGE_1)
                .passageText("p").totalQuestions(13).correctAnswers(8)
                .score(new BigDecimal(band)).submittedAt(at).build());
        return quiz;
    }

    private void listening(User owner, TestMode mode, String band, LocalDateTime at) {
        ListeningTest test = listeningTestRepository.save(ListeningTest.builder()
                .user(owner).testMode(mode).score(new BigDecimal(band))
                .totalQuestions(10).correctAnswers(7).build());
        stamp("listening_tests", "test_id", test.getTestId(), at);
    }

    private void writing(User owner, String band, LocalDateTime at) {
        WritingPrompt prompt = writingPromptRepository.findAll().get(0);
        WritingSubmission essay = writingSubmissionRepository.save(WritingSubmission.builder()
                .user(owner).prompt(prompt).essayText("essay").wordCount(260)
                .overallBand(new BigDecimal(band)).taskResponseScore(new BigDecimal(band))
                .coherenceScore(new BigDecimal(band)).lexicalScore(new BigDecimal(band))
                .grammarScore(new BigDecimal(band)).errorListJson("[]").build());
        stamp("writing_submissions", "submission_id", essay.getSubmissionId(), at);
    }

    private void mock(User owner, SubmissionStatus status, String band, LocalDateTime at) {
        MockTest paper = mockTestRepository.findAll().get(0);
        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(owner).mockTest(paper).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0).progressJson("{}").build());
        MockTestSubmission sub = submissionRepository.save(MockTestSubmission.builder()
                .user(owner).mockTest(paper).sessionId(session.getSessionId()).status(status)
                .listeningScore(new BigDecimal("6.0")).readingScore(new BigDecimal("5.5"))
                .writingScore(band == null ? BigDecimal.ZERO : new BigDecimal(band))
                .overallBand(band == null ? BigDecimal.ZERO : new BigDecimal(band))
                .listeningCorrectAnswers(0).readingCorrectAnswers(0).build());
        stamp("mock_test_submissions", "submission_id", sub.getSubmissionId(), at);
        // The sitting took ten minutes.
        jdbc.update("UPDATE mock_test_sessions SET started_at = ? WHERE session_id = ?",
                at.minusMinutes(10), session.getSessionId());
    }

    /**
     * submitted_at is stamped "now" by @PrePersist and is updatable = false on the entity,
     * so the fixed times the ordering assertions need go in through SQL.
     */
    private void stamp(String table, String idColumn, Long id, LocalDateTime at) {
        entityManager.flush();
        jdbc.update("UPDATE " + table + " SET submitted_at = ? WHERE " + idColumn + " = ?", at, id);
    }

    private RequestPostProcessor asUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Test
    @DisplayName("one page merges the four skills newest first, and pages continue in order")
    void mergedNewestFirstAndPaged() throws Exception {
        mockMvc.perform(get("/api/v1/history").param("size", "4").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.content[*].skill",
                        contains("MOCK_TEST", "READING", "MOCK_TEST", "WRITING")))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].score").value(6.5))
                .andExpect(jsonPath("$.data.content[0].title").value(notNullValue()))
                .andExpect(jsonPath("$.data.content[1].title").value("SCIENCE"))
                .andExpect(jsonPath("$.data.content[2].status").value("GRADING"))
                .andExpect(jsonPath("$.data.content[3].refId").value(notNullValue()))
                // Time and review link: the mock test from its session, the SCIENCE quiz from
                // its score_history row, the essay from nothing (no row within 5 s).
                .andExpect(jsonPath("$.data.content[0].timeSpentSeconds").value(600))
                .andExpect(jsonPath("$.data.content[0].historyId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].timeSpentSeconds").value(1500))
                .andExpect(jsonPath("$.data.content[1].historyId").value(notNullValue()))
                .andExpect(jsonPath("$.data.content[3].timeSpentSeconds").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].historyId").value(nullValue()));

        mockMvc.perform(get("/api/v1/history").param("size", "4").param("page", "1").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].skill", contains("LISTENING", "READING")))
                .andExpect(jsonPath("$.data.content[0].title").value("PRACTICE"))
                .andExpect(jsonPath("$.data.content[0].historyId").value(notNullValue()))
                .andExpect(jsonPath("$.data.content[0].timeSpentSeconds").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].title").value("HEALTH"))
                // The HEALTH quiz's only score_history row is a minute later: not matched.
                .andExpect(jsonPath("$.data.content[1].historyId").value(nullValue()));
    }

    @Test
    @DisplayName("skill, from and q filter on the server")
    void filters() throws Exception {
        mockMvc.perform(get("/api/v1/history").param("skill", "reading").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].title", contains("SCIENCE", "HEALTH")));

        mockMvc.perform(get("/api/v1/history").param("from", T0.plusHours(3).toString()).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].skill", contains("MOCK_TEST", "READING", "MOCK_TEST")));

        mockMvc.perform(get("/api/v1/history").param("q", "scien").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("SCIENCE"));

        mockMvc.perform(get("/api/v1/history").param("skill", "SPEAKING").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("size is capped at 100 and an empty history is an empty page")
    void capAndEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/history").param("size", "9999").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        RequestPostProcessor asOther = authentication(new UsernamePasswordAuthenticationToken(
                other, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        mockMvc.perform(get("/api/v1/history").with(asOther))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("ENVIRONMENT"));
    }
}
