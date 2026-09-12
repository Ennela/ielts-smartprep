package com.smartprep.controller;

import com.smartprep.model.entity.*;
import com.smartprep.model.enums.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The five history endpoints and the template catalogue, over HTTP against the real
 * schema: each returns a Spring page, honours page/size, and the fetch graphs and window
 * queries behind them resolve inside the read-only transaction.
 *
 * <p>Every list used to return the user's entire history as a bare array. The frontend
 * hook and pagination component already spoke the {@code content / totalPages /
 * totalElements} shape, so that is the shape here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class HistoryPaginationIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private WritingSubmissionRepository writingSubmissionRepository;
    @Autowired private WritingFullSubmissionRepository writingFullSubmissionRepository;
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

    @BeforeEach
    void seed() {
        user = userRepository.save(User.builder()
                .username("pager").passwordHash("x").email("pager@example.test")
                .displayName("Pager").role(Role.STUDENT).build());
    }

    private RequestPostProcessor asUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    private WritingSubmission essay(WritingPrompt prompt, String band) {
        return writingSubmissionRepository.save(WritingSubmission.builder()
                .user(user).prompt(prompt).essayText("essay").wordCount(250)
                .overallBand(new BigDecimal(band)).taskResponseScore(new BigDecimal(band))
                .coherenceScore(new BigDecimal(band)).lexicalScore(new BigDecimal(band))
                .grammarScore(new BigDecimal(band)).errorListJson("[]").build());
    }

    @Test
    @DisplayName("writing history is paged, newest first, with prompts fetched alongside")
    void writingHistory() throws Exception {
        WritingPrompt prompt = writingPromptRepository.findAll().get(0);
        for (int i = 0; i < 3; i++) essay(prompt, "6.0");

        mockMvc.perform(get("/api/v1/writing/history").param("page", "0").param("size", "2").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].essayType").value(prompt.getEssayType().name()))
                .andExpect(jsonPath("$.data.content[0].promptTextPreview", not(emptyString())));

        mockMvc.perform(get("/api/v1/writing/history").param("page", "1").param("size", "2").with(asUser()))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("full writing history is paged and both tasks come with the sitting")
    void fullWritingHistory() throws Exception {
        List<WritingPrompt> prompts = writingPromptRepository.findAll();
        for (int i = 0; i < 2; i++) {
            writingFullSubmissionRepository.save(WritingFullSubmission.builder()
                    .user(user).task1Submission(essay(prompts.get(0), "5.5")).task2Submission(essay(prompts.get(1), "6.5"))
                    .overallBand(new BigDecimal("6.0")).build());
        }

        mockMvc.perform(get("/api/v1/writing/full-history").param("size", "1").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].task1Result.overallBand").value(5.5))
                .andExpect(jsonPath("$.data.content[0].task2Result.essayType").value(prompts.get(1).getEssayType().name()))
                .andExpect(jsonPath("$.data.content[0].overallWritingBand").value(6.0));
    }

    @Test
    @DisplayName("listening history is paged and still pairs each test with its history row")
    void listeningHistory() throws Exception {
        for (int i = 0; i < 2; i++) {
            listeningTestRepository.save(ListeningTest.builder()
                    .user(user).testMode(TestMode.PRACTICE).score(new BigDecimal("6.5"))
                    .totalQuestions(10).correctAnswers(7).build());
            scoreHistoryRepository.save(ScoreHistory.builder()
                    .user(user).skillType(SkillType.LISTENING).score(new BigDecimal("6.5"))
                    .difficulty("PART_1").timeSpentSeconds(600 + i).build());
        }

        mockMvc.perform(get("/api/v1/listening/history").param("size", "1").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].historyId", notNullValue()))
                .andExpect(jsonPath("$.data.content[0].timeSpentSeconds", notNullValue()));
    }

    @Test
    @DisplayName("reading history is paged and lists only submitted quizzes")
    void readingHistory() throws Exception {
        for (int i = 0; i < 3; i++) {
            ReadingQuiz quiz = ReadingQuiz.builder()
                    .user(user).topic(Topic.SCIENCE).difficulty(Difficulty.PASSAGE_1)
                    .passageText("p").totalQuestions(5).correctAnswers(3).build();
            if (i < 2) {
                quiz.setScore(new BigDecimal("5.5"));
                quiz.setSubmittedAt(java.time.LocalDateTime.now().minusMinutes(i));
            }
            readingQuizRepository.save(quiz);
        }

        mockMvc.perform(get("/api/v1/reading/history").param("size", "5").with(asUser()))
                .andExpect(status().isOk())
                // The third quiz was never submitted and must not appear.
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].bandScore").value(5.5));
    }

    @Test
    @DisplayName("mock test history is paged with the paper fetched alongside and per-skill bands")
    void mockTestHistory() throws Exception {
        MockTest paper = mockTestRepository.findAll().get(0);
        for (int i = 0; i < 2; i++) {
            MockTestSession session = sessionRepository.save(MockTestSession.builder()
                    .user(user).mockTest(paper).status(SessionStatus.SUBMITTED)
                    .currentSection(SkillType.WRITING).timeRemainingSeconds(0).progressJson("{}").build());
            submissionRepository.save(MockTestSubmission.builder()
                    .user(user).mockTest(paper).sessionId(session.getSessionId())
                    .status(i == 0 ? SubmissionStatus.COMPLETED : SubmissionStatus.GRADING)
                    .listeningScore(new BigDecimal("6.0")).readingScore(new BigDecimal("5.5"))
                    .writingScore(i == 0 ? new BigDecimal("6.0") : BigDecimal.ZERO)
                    .overallBand(i == 0 ? new BigDecimal("6.0") : BigDecimal.ZERO)
                    .listeningCorrectAnswers(0).readingCorrectAnswers(0).build());
        }

        mockMvc.perform(get("/api/v1/mock-tests/history").param("size", "10").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].title", everyItem(equalTo(paper.getTitle()))))
                .andExpect(jsonPath("$.data.content[*].listeningScore", everyItem(equalTo(6.0))))
                // One of the two is still grading: its writing band is absent, not zero.
                .andExpect(jsonPath("$.data.content[*].writingScore", hasItem(nullValue())));
    }

    @Test
    @DisplayName("the template catalogue carries a question count and no questions")
    void templateCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/reading/templates").param("size", "3").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", not(empty())))
                .andExpect(jsonPath("$.data.content[0].totalQuestions", greaterThan(0)))
                .andExpect(jsonPath("$.data.content[0].questions").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].passageText", not(emptyString())));
    }

    @Test
    @DisplayName("an oversized page size is clamped rather than honoured")
    void sizeIsClamped() throws Exception {
        mockMvc.perform(get("/api/v1/writing/history").param("size", "9999").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }
}
