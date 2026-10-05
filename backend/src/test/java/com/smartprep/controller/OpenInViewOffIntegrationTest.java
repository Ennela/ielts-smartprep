package com.smartprep.controller;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.ListeningTestPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.SpeakingAnswer;
import com.smartprep.model.entity.SpeakingSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.model.enums.TestMode;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.SeededContentFixture;
import com.smartprep.repository.SpeakingPromptRepository;
import com.smartprep.repository.SpeakingSubmissionRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.security.RateLimitInterceptor;
import com.smartprep.service.LoginLockoutService;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every request that used to walk a lazy association outside a transaction, run with
 * {@code spring.jpa.open-in-view} off (issue #17). Without the open-in-view EntityManager a
 * lazy load after the transaction has ended throws LazyInitializationException, which the
 * API answers with a 500 -- so each case asserts the success status and some of the data the walk produced.
 *
 * <p>Gemini is a mock: it fails where the request has a fallback built from stored content
 * (the walk this class is about), and answers where the walk happens before the call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OpenInViewOffIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ReadingQuizRepository readingQuizRepository;
    @Autowired private ListeningPartRepository listeningPartRepository;
    @Autowired private ListeningTestRepository listeningTestRepository;
    @Autowired private ScoreHistoryRepository scoreHistoryRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private MockTestSessionRepository mockTestSessionRepository;
    @Autowired private MockTestSubmissionRepository mockTestSubmissionRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private WritingSubmissionRepository writingSubmissionRepository;
    @Autowired private SpeakingPromptRepository speakingPromptRepository;
    @Autowired private SpeakingSubmissionRepository speakingSubmissionRepository;

    @MockBean private GeminiClient geminiClient;
    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;
    // The AI rate limit needs Redis; it is not what this class is about.
    @MockBean private RateLimitInterceptor rateLimitInterceptor;

    @Value("${spring.jpa.open-in-view}")
    private boolean openInView;

    private User learner;
    private User admin;
    private ListeningPart part;

    @BeforeEach
    void seed() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        learner = userRepository.save(User.builder().username("osiv_" + tag).passwordHash("x")
                .email("osiv_" + tag + "@example.test").role(Role.STUDENT).build());
        admin = userRepository.save(User.builder().username("osiv_admin_" + tag).passwordHash("x")
                .email("osiv_admin_" + tag + "@example.test").role(Role.ADMIN).build());

        // A reading template with a question and its options: the fallback clones all three levels.
        ReadingQuiz template = ReadingQuiz.builder().topic(Topic.ENVIRONMENT).difficulty(Difficulty.PASSAGE_1)
                .passageText("A passage about rivers.").timeLimitSeconds(900).totalQuestions(1)
                .isTemplate(true).moduleType("ACADEMIC").build();
        ReadingQuestion rq = ReadingQuestion.builder().quiz(template).questionType(QuestionType.MCQ)
                .questionText("What is the passage about?").correctAnswer("A").orderIndex(1).build();
        rq.setOptions(List.of(
                QuestionOption.builder().readingQuestion(rq).label("A").content("Rivers").isCorrect(true).orderIndex(1).build(),
                QuestionOption.builder().readingQuestion(rq).label("B").content("Roads").isCorrect(false).orderIndex(2).build()));
        template.setQuestions(List.of(rq));
        readingQuizRepository.save(template);

        // A published listening part, the source of every listening fallback.
        part = ListeningPart.builder().partNumber(1).title("OSIV part " + tag).topic("travel")
                .audioUrl("osiv.mp3").audioStatus(AudioStatus.READY).contentStatus(ContentStatus.PUBLISHED)
                .transcriptText("Good morning, the train leaves at nine.").durationSeconds(60).build();
        ListeningQuestion lq = ListeningQuestion.builder().part(part).questionType(QuestionType.MCQ)
                .questionText("When does the train leave?").correctAnswer("A").orderIndex(1).build();
        lq.setOptions(List.of(
                QuestionOption.builder().listeningQuestion(lq).label("A").content("Nine").isCorrect(true).orderIndex(1).build(),
                QuestionOption.builder().listeningQuestion(lq).label("B").content("Ten").isCorrect(false).orderIndex(2).build()));
        part.setQuestions(List.of(lq));
        part = listeningPartRepository.save(part);
    }

    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    private ListeningTest submittedListeningTest() {
        ListeningTest test = ListeningTest.builder().user(learner).testMode(TestMode.PRACTICE)
                .submittedAt(LocalDateTime.now()).build();
        test.getTestParts().add(ListeningTestPart.builder().test(test).part(part).build());
        return listeningTestRepository.save(test);
    }

    private void readingHistoryWithAnswers() {
        ScoreHistory history = ScoreHistory.builder().user(learner).skillType(SkillType.READING)
                .score(new BigDecimal("6.0")).difficulty("PASSAGE_1").recordedAt(LocalDateTime.now()).build();
        history.getUserAnswers().add(UserAnswer.builder().scoreHistory(history).questionNo(1)
                .questionText("Q").questionType("MCQ").correctAnswer("A").userAnswer("B").isCorrect(false).build());
        scoreHistoryRepository.save(history);
    }

    @Test
    @DisplayName("runs with open-in-view off")
    void openInViewIsOff() {
        assertThat(openInView).isFalse();
    }

    @Test
    @DisplayName("Reading generation falls back to cloning a stored quiz, questions and options")
    void readingFallback() throws Exception {
        when(geminiClient.generate(anyString(), anyString())).thenThrow(new IllegalStateException("stub: Gemini down"));

        mockMvc.perform(post("/api/v1/reading/generate").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"ENVIRONMENT\", \"difficulty\": \"PASSAGE_1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questions").isNotEmpty());
    }

    @Test
    @DisplayName("adaptive Reading reads the learner's recent answers before generating")
    void readingAdaptive() throws Exception {
        readingHistoryWithAnswers();
        when(geminiClient.generate(anyString(), anyString())).thenThrow(new IllegalStateException("stub: Gemini down"));

        mockMvc.perform(post("/api/v1/reading/generate").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"ENVIRONMENT\", \"difficulty\": \"ADAPTIVE\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/adaptive/next").param("skill", "READING").with(as(learner)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Listening generation falls back to a stored part, with its questions and options")
    void listeningFallback() throws Exception {
        when(geminiClient.generate(anyString(), anyString())).thenThrow(new IllegalStateException("stub: Gemini down"));

        mockMvc.perform(post("/api/v1/listening/generate").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNumber\": 1, \"topic\": \"travel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions").isNotEmpty());
        mockMvc.perform(post("/api/v1/listening/generate-mock").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\": \"travel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    @DisplayName("analysing a Listening question reads its part's transcript")
    void listeningAnalyse() throws Exception {
        submittedListeningTest();
        Long questionId = part.getQuestions().get(0).getQuestionId();
        when(geminiClient.generate(anyString(), contains("Good morning, the train leaves at nine.")))
                .thenReturn("{\"tip\": \"Listen for times.\"}");

        mockMvc.perform(post("/api/v1/listening/ai-analyze/" + questionId).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tip").value("Listen for times."));
    }

    @Test
    @DisplayName("vocabulary suggestions gather text from a listening test, an essay and a mock test")
    void vocabularySources() throws Exception {
        ListeningTest listeningTest = submittedListeningTest();
        WritingSubmission essay = writingSubmissionRepository.save(WritingSubmission.builder()
                .user(learner).prompt(writingPromptRepository.findAll().get(0)).essayText("essay").wordCount(1)
                .overallBand(new BigDecimal("5.0")).taskResponseScore(new BigDecimal("5.0"))
                .coherenceScore(new BigDecimal("5.0")).lexicalScore(new BigDecimal("5.0"))
                .grammarScore(new BigDecimal("5.0")).build());
        SeededContentFixture.restorePaper(jdbcTemplate);
        MockTest paper = mockTestRepository.findAll().stream()
                .filter(m -> SeededContentFixture.PAPER_TITLE.equals(m.getTitle())).findFirst().orElseThrow();
        MockTestSession session = mockTestSessionRepository.save(MockTestSession.builder()
                .user(learner).mockTest(paper).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0).build());
        MockTestSubmission sitting = mockTestSubmissionRepository.save(MockTestSubmission.builder()
                .user(learner).mockTest(paper).sessionId(session.getSessionId()).status(SubmissionStatus.COMPLETED)
                .listeningCorrectAnswers(0).readingCorrectAnswers(0).writingTask1Submission(essay).build());
        when(geminiClient.generateAndParse(anyString(), anyString(), any())).thenReturn(null);

        for (String[] source : List.of(
                new String[]{"LISTENING", listeningTest.getTestId().toString()},
                new String[]{"WRITING", essay.getSubmissionId().toString()},
                new String[]{"MOCK_TEST", sitting.getSubmissionId().toString()})) {
            mockMvc.perform(post("/api/v1/vocab/ai-suggest").with(as(learner)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"skillType\": \"" + source[0] + "\", \"sourceId\": " + source[1] + "}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("admin lists of reading quizzes and mock tests map their collections")
    void adminLists() throws Exception {
        SeededContentFixture.restorePaper(jdbcTemplate);

        mockMvc.perform(get("/api/v1/admin/reading-quizzes").param("size", "50").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/mock-tests").param("size", "50").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].listeningPartIds").isArray());
    }

    @Test
    @DisplayName("a Speaking result and the history load their prompt and answers")
    void speakingResult() throws Exception {
        SpeakingSubmission submission = SpeakingSubmission.builder().user(learner)
                .prompt(speakingPromptRepository.findByPartOrderByPromptIdAsc(1).get(0)).durationSeconds(20)
                .overallBand(new BigDecimal("6.0")).fluencyBand(new BigDecimal("6.0"))
                .lexicalBand(new BigDecimal("6.0")).grammarBand(new BigDecimal("6.0"))
                .pronunciationBand(new BigDecimal("6.0")).feedbackJson("{}").build();
        submission.getAnswers().add(SpeakingAnswer.builder().submission(submission).questionIndex(0)
                .audioKey("k.webm").audioMimeType("audio/webm").durationSeconds(20).transcript("Hue.").build());
        Long id = speakingSubmissionRepository.save(submission).getSubmissionId();

        mockMvc.perform(get("/api/v1/speaking/submissions/" + id).with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].transcript").value("Hue."));
        mockMvc.perform(get("/api/v1/speaking/submissions").with(as(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].part").value(1));
    }
}
