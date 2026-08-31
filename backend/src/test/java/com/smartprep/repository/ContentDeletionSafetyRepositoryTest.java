package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.ListeningTestPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSection;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.MockTestDifficulty;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.model.enums.TestMode;
import com.smartprep.model.enums.Topic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ContentDeletionSafetyRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReadingQuizRepository readingQuizRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private ListeningPartRepository listeningPartRepository;
    @Autowired private MockTestRepository mockTestRepository;

    @Test
    void archivingReadingOnlyPreservesQuestionsMockLinkAndOtherActiveContent() {
        ReadingQuiz quiz = readingQuizWithQuestion();
        entityManager.persistAndFlush(quiz);
        Long questionId = quiz.getQuestions().get(0).getQuestionId();
        Long optionId = quiz.getQuestions().get(0).getOptions().get(0).getOptionId();

        ListeningPart part = listeningPartWithQuestion();
        entityManager.persistAndFlush(part);

        WritingPrompt prompt = entityManager.persistAndFlush(writingPrompt());

        MockTest mockTest = entityManager.persistAndFlush(MockTest.builder()
                .title("Reading isolation mock")
                .difficulty(MockTestDifficulty.MEDIUM)
                .readingQuizzes(new ArrayList<>(List.of(quiz)))
                .listeningParts(new ArrayList<>(List.of(part)))
                .writingPrompts(new ArrayList<>(List.of(prompt)))
                .build());

        quiz.setDeletedAt(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(readingQuizRepository.findById(quiz.getQuizId())).isEmpty();
        assertThat(readingQuizRepository.findIncludingDeletedById(quiz.getQuizId())).isPresent();
        assertThat(listeningPartRepository.findById(part.getPartId())).isPresent();
        assertThat(writingPromptRepository.findById(prompt.getPromptId())).isPresent();
        assertThat(mockTestRepository.findById(mockTest.getMockTestId())).isPresent();

        assertCount("reading_quizzes", "quiz_id", quiz.getQuizId(), 1);
        assertCount("reading_questions", "question_id", questionId, 1);
        assertCount("question_options", "option_id", optionId, 1);
        assertCount("mock_test_reading_quizzes", "mock_test_id", mockTest.getMockTestId(), 1);
    }

    @Test
    void archivingWritingOnlyPreservesSubmissionMockLinkAndActiveMock() {
        User user = persistUser("writing");
        WritingPrompt prompt = entityManager.persistAndFlush(writingPrompt());
        WritingSubmission submission = entityManager.persistAndFlush(WritingSubmission.builder()
                .user(user)
                .prompt(prompt)
                .essayText("A dummy user submission that must be retained.")
                .wordCount(9)
                .build());
        MockTest mockTest = entityManager.persistAndFlush(MockTest.builder()
                .title("Writing isolation mock")
                .difficulty(MockTestDifficulty.MEDIUM)
                .writingPrompts(new ArrayList<>(List.of(prompt)))
                .build());

        prompt.setDeletedAt(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(writingPromptRepository.findById(prompt.getPromptId())).isEmpty();
        assertThat(writingPromptRepository.findIncludingDeletedById(prompt.getPromptId())).isPresent();
        assertThat(mockTestRepository.findById(mockTest.getMockTestId())).isPresent();
        assertCount("writing_prompts", "prompt_id", prompt.getPromptId(), 1);
        assertCount("writing_submissions", "submission_id", submission.getSubmissionId(), 1);
        assertCount("mock_test_writing_prompts", "mock_test_id", mockTest.getMockTestId(), 1);
    }

    @Test
    void archivingListeningOnlyPreservesQuestionsAudioHistoryMockLinkAndActiveMock() {
        User user = persistUser("listening");
        ListeningPart part = listeningPartWithQuestion();
        entityManager.persistAndFlush(part);
        Long questionId = part.getQuestions().get(0).getQuestionId();
        Long optionId = part.getQuestions().get(0).getOptions().get(0).getOptionId();

        ListeningTest listeningTest = ListeningTest.builder()
                .user(user)
                .testMode(TestMode.PRACTICE)
                .totalQuestions(1)
                .correctAnswers(1)
                .build();
        ListeningTestPart testPart = ListeningTestPart.builder()
                .test(listeningTest)
                .part(part)
                .userAnswersJson("{\"1\":\"A\"}")
                .build();
        listeningTest.setTestParts(new ArrayList<>(List.of(testPart)));
        entityManager.persistAndFlush(listeningTest);

        MockTest mockTest = entityManager.persistAndFlush(MockTest.builder()
                .title("Listening isolation mock")
                .difficulty(MockTestDifficulty.MEDIUM)
                .listeningParts(new ArrayList<>(List.of(part)))
                .build());

        String originalAudioUrl = part.getAudioUrl();
        part.setDeletedAt(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(listeningPartRepository.findById(part.getPartId())).isEmpty();
        assertThat(listeningPartRepository.findIncludingDeletedById(part.getPartId()))
                .get()
                .extracting(ListeningPart::getAudioUrl)
                .isEqualTo(originalAudioUrl);
        assertThat(mockTestRepository.findById(mockTest.getMockTestId())).isPresent();
        assertCount("listening_parts", "part_id", part.getPartId(), 1);
        assertCount("listening_questions", "question_id", questionId, 1);
        assertCount("question_options", "option_id", optionId, 1);
        assertCount("listening_test_parts", "id", testPart.getId(), 1);
        assertCount("mock_test_listening_parts", "mock_test_id", mockTest.getMockTestId(), 1);
    }

    @Test
    void archivingMockOnlyPreservesSharedContentLinksSectionsSessionsAndSubmission() {
        User user = persistUser("mock");
        ReadingQuiz quiz = readingQuizWithQuestion();
        ListeningPart part = listeningPartWithQuestion();
        WritingPrompt prompt = writingPrompt();
        entityManager.persist(quiz);
        entityManager.persist(part);
        entityManager.persist(prompt);
        entityManager.flush();

        MockTest mockTest = MockTest.builder()
                .title("Mock isolation test")
                .difficulty(MockTestDifficulty.MEDIUM)
                .readingQuizzes(new ArrayList<>(List.of(quiz)))
                .listeningParts(new ArrayList<>(List.of(part)))
                .writingPrompts(new ArrayList<>(List.of(prompt)))
                .build();
        MockTestSection section = MockTestSection.builder()
                .mockTest(mockTest)
                .sectionType(SkillType.READING)
                .durationSeconds(3600)
                .sectionOrder(1)
                .build();
        mockTest.setSections(new ArrayList<>(List.of(section)));
        entityManager.persistAndFlush(mockTest);

        MockTestSession session = entityManager.persistAndFlush(MockTestSession.builder()
                .user(user)
                .mockTest(mockTest)
                .status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING)
                .timeRemainingSeconds(0)
                .progressJson("{}")
                .build());
        MockTestSubmission submission = entityManager.persistAndFlush(MockTestSubmission.builder()
                .user(user)
                .mockTest(mockTest)
                .sessionId(session.getSessionId())
                .status(SubmissionStatus.COMPLETED)
                .build());

        mockTest.setDeletedAt(LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        assertThat(mockTestRepository.findById(mockTest.getMockTestId())).isEmpty();
        assertThat(mockTestRepository.findIncludingDeletedById(mockTest.getMockTestId())).isPresent();
        assertThat(readingQuizRepository.findById(quiz.getQuizId())).isPresent();
        assertThat(listeningPartRepository.findById(part.getPartId())).isPresent();
        assertThat(writingPromptRepository.findById(prompt.getPromptId())).isPresent();
        assertCount("mock_test_reading_quizzes", "mock_test_id", mockTest.getMockTestId(), 1);
        assertCount("mock_test_listening_parts", "mock_test_id", mockTest.getMockTestId(), 1);
        assertCount("mock_test_writing_prompts", "mock_test_id", mockTest.getMockTestId(), 1);
        assertCount("mock_test_sections", "section_id", section.getSectionId(), 1);
        assertCount("mock_test_sessions", "session_id", session.getSessionId(), 1);
        assertCount("mock_test_submissions", "submission_id", submission.getSubmissionId(), 1);
    }

    @Test
    void v41KeepsDirectChildCascadesAndRestrictsSharedOrHistoricalReferences() {
        assertDeleteRule("reading_questions", "quiz_id", "CASCADE");
        assertDeleteRule("listening_questions", "part_id", "CASCADE");
        assertDeleteRule("question_options", "reading_question_id", "CASCADE");
        assertDeleteRule("question_options", "listening_question_id", "CASCADE");

        assertDeleteRule("mock_test_reading_quizzes", "quiz_id", "RESTRICT");
        assertDeleteRule("mock_test_listening_parts", "part_id", "RESTRICT");
        assertDeleteRule("mock_test_writing_prompts", "prompt_id", "RESTRICT");
        assertDeleteRule("writing_submissions", "prompt_id", "RESTRICT");
        assertDeleteRule("listening_test_parts", "part_id", "RESTRICT");

        assertDeleteRule("mock_test_reading_quizzes", "mock_test_id", "CASCADE");
        assertDeleteRule("mock_test_listening_parts", "mock_test_id", "CASCADE");
        assertDeleteRule("mock_test_writing_prompts", "mock_test_id", "CASCADE");
        assertDeleteRule("mock_test_sections", "mock_test_id", "CASCADE");
    }

    private ReadingQuiz readingQuizWithQuestion() {
        ReadingQuiz quiz = ReadingQuiz.builder()
                .topic(Topic.TECHNOLOGY)
                .difficulty(Difficulty.PASSAGE_1)
                .passageText("Dummy passage for deletion-safety integration testing.")
                .isTemplate(true)
                .build();
        ReadingQuestion question = ReadingQuestion.builder()
                .quiz(quiz)
                .questionType(QuestionType.MCQ)
                .questionText("Dummy question?")
                .correctAnswer("A")
                .orderIndex(1)
                .build();
        QuestionOption option = QuestionOption.builder()
                .readingQuestion(question)
                .label("A")
                .content("Dummy answer")
                .isCorrect(true)
                .orderIndex(1)
                .build();
        question.setOptions(new ArrayList<>(List.of(option)));
        quiz.setQuestions(new ArrayList<>(List.of(question)));
        return quiz;
    }

    private ListeningPart listeningPartWithQuestion() {
        ListeningPart part = ListeningPart.builder()
                .partNumber(1)
                .title("Dummy deletion-safety listening part")
                .topic("test")
                .audioUrl("/api/v1/listening/audio/dummy-delete-safety.mp3")
                .audioStatus(AudioStatus.READY)
                .transcriptText("Dummy transcript.")
                .createdBy("TEST")
                .build();
        ListeningQuestion question = ListeningQuestion.builder()
                .part(part)
                .questionType(QuestionType.MCQ)
                .questionText("Dummy listening question?")
                .correctAnswer("A")
                .orderIndex(1)
                .verified(true)
                .build();
        QuestionOption option = QuestionOption.builder()
                .listeningQuestion(question)
                .label("A")
                .content("Dummy listening answer")
                .isCorrect(true)
                .orderIndex(1)
                .build();
        question.setOptions(new ArrayList<>(List.of(option)));
        part.setQuestions(new ArrayList<>(List.of(question)));
        return part;
    }

    private WritingPrompt writingPrompt() {
        return WritingPrompt.builder()
                .promptText("Dummy Task 1 prompt used only by deletion-safety test")
                .essayType(EssayType.LINE_GRAPH)
                .taskType(WritingTaskType.TASK_1)
                .imageUrl("/dummy/task1.png")
                .build();
    }

    private User persistUser(String suffix) {
        return entityManager.persistAndFlush(User.builder()
                .username("delete_safety_" + suffix)
                .email("delete-safety-" + suffix + "@test.invalid")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
    }

    private void assertCount(String table, String idColumn, Long id, int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ?",
                Integer.class,
                id);
        assertThat(count).isEqualTo(expected);
    }

    private void assertDeleteRule(String table, String column, String expectedRule) {
        String rule = jdbcTemplate.queryForObject("""
                SELECT rc.DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS rc
                JOIN information_schema.KEY_COLUMN_USAGE kcu
                  ON rc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA
                 AND rc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
                 AND rc.TABLE_NAME = kcu.TABLE_NAME
                WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
                  AND rc.TABLE_NAME = ?
                  AND kcu.COLUMN_NAME = ?
                """, String.class, table, column);
        assertThat(rule).isEqualTo(expectedRule);
    }
}
