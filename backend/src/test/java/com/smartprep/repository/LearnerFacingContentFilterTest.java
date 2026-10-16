package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.ListeningTestPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.MockTestDifficulty;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.TestMode;
import com.smartprep.model.enums.Topic;
import com.smartprep.model.enums.WritingTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learner-facing queries return reviewed content only, and the admin ones still return
 * everything.
 *
 * <p>Run against MySQL because the filter is JPQL with an enum constant in it; a mocked
 * repository would prove nothing about whether it parses or what it selects. Each test
 * compares a learner query with its admin twin over the same rows, so seeded content in
 * the database cannot make it pass by accident.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class LearnerFacingContentFilterTest extends AbstractMySQLContainerTest {

    private static final String MARK = "learner-filter-test";

    @Autowired private TestEntityManager entityManager;
    @Autowired private ReadingQuizRepository readingQuizRepository;
    @Autowired private ListeningPartRepository listeningPartRepository;
    @Autowired private WritingPromptRepository writingPromptRepository;
    @Autowired private MockTestRepository mockTestRepository;

    private User author;

    @BeforeEach
    void setUp() {
        author = entityManager.persistAndFlush(User.builder()
                .username("filter_author").email("filter-author@test.com")
                .passwordHash("hash").role(Role.STUDENT).build());
    }

    @Test
    @DisplayName("reading: a draft template is in the admin list and not in the learner catalogue")
    void reading() {
        ReadingQuiz published = entityManager.persistAndFlush(quiz(ContentStatus.PUBLISHED));
        ReadingQuiz draft = entityManager.persistAndFlush(quiz(ContentStatus.DRAFT));
        ReadingQuiz aiImported = entityManager.persistAndFlush(quiz(ContentStatus.AI_IMPORTED));

        var learner = readingQuizRepository.findPublishedQuizzes(Topic.SCIENCE, Difficulty.PASSAGE_3, "ADMIN",
                PageRequest.of(0, 100)).getContent();
        var admin = readingQuizRepository.findQuizzesForAdmin(Topic.SCIENCE, Difficulty.PASSAGE_3, "ADMIN",
                PageRequest.of(0, 100)).getContent();

        assertThat(learner).extracting(ReadingQuiz::getQuizId).contains(published.getQuizId())
                .doesNotContain(draft.getQuizId(), aiImported.getQuizId());
        assertThat(admin).extracting(ReadingQuiz::getQuizId)
                .contains(published.getQuizId(), draft.getQuizId(), aiImported.getQuizId());
    }

    @Test
    @DisplayName("reading bank: only published templates the learner has not had a copy of")
    void readingBank() {
        ReadingQuiz seen = entityManager.persistAndFlush(quiz(ContentStatus.PUBLISHED));
        ReadingQuiz unseen = entityManager.persistAndFlush(quiz(ContentStatus.PUBLISHED));
        ReadingQuiz imported = entityManager.persistAndFlush(quiz(ContentStatus.AI_IMPORTED));
        User learner = entityManager.persistAndFlush(User.builder()
                .username("bank_learner").email("bank-learner@test.com")
                .passwordHash("hash").role(Role.STUDENT).build());
        entityManager.persistAndFlush(ReadingQuiz.builder().user(learner).topic(Topic.SCIENCE)
                .difficulty(Difficulty.PASSAGE_3).passageText(MARK).isTemplate(false)
                .parentTemplateId(seen.getQuizId()).build());

        var forLearner = readingQuizRepository.findUnseenPublishedTemplates(
                Topic.SCIENCE, Difficulty.PASSAGE_3, learner.getUserId(), PageRequest.of(0, 100));
        var forAuthor = readingQuizRepository.findUnseenPublishedTemplates(
                Topic.SCIENCE, Difficulty.PASSAGE_3, author.getUserId(), PageRequest.of(0, 100));

        assertThat(forLearner).extracting(ReadingQuiz::getQuizId).contains(unseen.getQuizId())
                .doesNotContain(seen.getQuizId(), imported.getQuizId());
        assertThat(forAuthor).extracting(ReadingQuiz::getQuizId).contains(seen.getQuizId(), unseen.getQuizId())
                .doesNotContain(imported.getQuizId());
    }

    @Test
    @DisplayName("listening: a draft part is neither listed nor used as a fallback")
    void listening() {
        ListeningPart published = entityManager.persistAndFlush(part(ContentStatus.PUBLISHED));
        ListeningPart draft = entityManager.persistAndFlush(part(ContentStatus.DRAFT));

        assertThat(listeningPartRepository.findPublishedOrderByPartNumber())
                .extracting(ListeningPart::getPartId).contains(published.getPartId())
                .doesNotContain(draft.getPartId());
        assertThat(listeningPartRepository.findPublishedByPartNumber(4))
                .extracting(ListeningPart::getPartId).contains(published.getPartId())
                .doesNotContain(draft.getPartId());
        assertThat(listeningPartRepository.findAllByOrderByPartNumberAscPartIdAsc())
                .extracting(ListeningPart::getPartId).contains(published.getPartId(), draft.getPartId());
    }

    @Test
    @DisplayName("listening bank: published parts with audio, on the topic, in none of the learner's tests")
    void listeningBank() {
        ListeningPart seen = entityManager.persistAndFlush(bankPart(ContentStatus.PUBLISHED, AudioStatus.READY, "Accommodation booking"));
        ListeningPart unseen = entityManager.persistAndFlush(bankPart(ContentStatus.PUBLISHED, AudioStatus.READY, "Accommodation"));
        ListeningPart noAudio = entityManager.persistAndFlush(bankPart(ContentStatus.PUBLISHED, AudioStatus.PENDING, "Accommodation"));
        ListeningPart imported = entityManager.persistAndFlush(bankPart(ContentStatus.AI_IMPORTED, AudioStatus.READY, "Accommodation"));
        ListeningPart otherTopic = entityManager.persistAndFlush(bankPart(ContentStatus.PUBLISHED, AudioStatus.READY, "Museum tour"));
        ListeningPart archived = bankPart(ContentStatus.PUBLISHED, AudioStatus.READY, "Accommodation");
        archived.setDeletedAt(java.time.LocalDateTime.now());
        entityManager.persistAndFlush(archived);

        User learner = entityManager.persistAndFlush(User.builder()
                .username("listening_bank_learner").email("listening-bank-learner@test.com")
                .passwordHash("hash").role(Role.STUDENT).build());
        ListeningTest test = ListeningTest.builder().user(learner).testMode(TestMode.PRACTICE).build();
        test.getTestParts().add(ListeningTestPart.builder().test(test).part(seen).build());
        entityManager.persistAndFlush(test);

        var onTopic = listeningPartRepository.findUnseenPublishedParts(2, "ACCOMMODATION", learner.getUserId(), PageRequest.of(0, 100));
        var anyTopic = listeningPartRepository.findUnseenPublishedParts(2, null, learner.getUserId(), PageRequest.of(0, 100));
        var forAuthor = listeningPartRepository.findUnseenPublishedParts(2, "accommodation", author.getUserId(), PageRequest.of(0, 100));

        assertThat(onTopic).extracting(ListeningPart::getPartId).contains(unseen.getPartId())
                .doesNotContain(seen.getPartId(), noAudio.getPartId(), imported.getPartId(),
                        otherTopic.getPartId(), archived.getPartId());
        assertThat(anyTopic).extracting(ListeningPart::getPartId).contains(unseen.getPartId(), otherTopic.getPartId())
                .doesNotContain(seen.getPartId());
        assertThat(forAuthor).extracting(ListeningPart::getPartId).contains(seen.getPartId(), unseen.getPartId());
        assertThat(listeningPartRepository.findUnseenPublishedParts(3, null, learner.getUserId(), PageRequest.of(0, 100)))
                .extracting(ListeningPart::getPartId).doesNotContain(unseen.getPartId());
    }

    @Test
    @DisplayName("writing bank: published prompts of the task's types, on the topic (any when none), never written by the learner")
    void writingBank() {
        WritingPrompt seen = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.BAR_CHART, Topic.HEALTH));
        WritingPrompt unseen = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.MAP, Topic.HEALTH));
        WritingPrompt letter = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.LETTER, Topic.HEALTH));
        WritingPrompt imported = entityManager.persistAndFlush(bankPrompt(ContentStatus.AI_IMPORTED, EssayType.LINE_GRAPH, Topic.HEALTH));
        WritingPrompt untagged = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.TABLE, null));
        WritingPrompt otherTopic = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.PIE_CHART, Topic.EDUCATION));

        User learner = entityManager.persistAndFlush(User.builder()
                .username("writing_bank_learner").email("writing-bank-learner@test.com")
                .passwordHash("hash").role(Role.STUDENT).build());
        entityManager.persistAndFlush(WritingSubmission.builder().user(learner).prompt(seen).essayText(MARK).build());

        List<EssayType> academic = List.of(EssayType.LINE_GRAPH, EssayType.BAR_CHART, EssayType.PIE_CHART,
                EssayType.TABLE, EssayType.MAP, EssayType.DIAGRAM);
        var onTopic = writingPromptRepository.findUnseenPublished(
                WritingTaskType.TASK_1, academic, Topic.HEALTH, learner.getUserId(), PageRequest.of(0, 100));
        var anyTopic = writingPromptRepository.findUnseenPublished(
                WritingTaskType.TASK_1, academic, null, learner.getUserId(), PageRequest.of(0, 100));
        var letters = writingPromptRepository.findUnseenPublished(
                WritingTaskType.TASK_1, List.of(EssayType.LETTER), Topic.HEALTH, learner.getUserId(), PageRequest.of(0, 100));
        var forAuthor = writingPromptRepository.findUnseenPublished(
                WritingTaskType.TASK_1, academic, Topic.HEALTH, author.getUserId(), PageRequest.of(0, 100));

        assertThat(onTopic).extracting(WritingPrompt::getPromptId).contains(unseen.getPromptId())
                .doesNotContain(seen.getPromptId(), letter.getPromptId(), imported.getPromptId(),
                        untagged.getPromptId(), otherTopic.getPromptId());
        assertThat(anyTopic).extracting(WritingPrompt::getPromptId)
                .contains(unseen.getPromptId(), untagged.getPromptId(), otherTopic.getPromptId())
                .doesNotContain(seen.getPromptId(), letter.getPromptId(), imported.getPromptId());
        assertThat(letters).extracting(WritingPrompt::getPromptId).contains(letter.getPromptId())
                .doesNotContain(unseen.getPromptId());
        assertThat(forAuthor).extracting(WritingPrompt::getPromptId).contains(seen.getPromptId(), unseen.getPromptId());
    }

    @Test
    @DisplayName("V60: writing_prompts.topic is in the schema Hibernate validated, stored as the enum name")
    void v60WritingPromptTopic() {
        WritingPrompt saved = entityManager.persistAndFlush(bankPrompt(ContentStatus.PUBLISHED, EssayType.OPINION, Topic.SCIENCE));
        entityManager.clear();

        Object column = entityManager.getEntityManager()
                .createNativeQuery("SELECT topic FROM writing_prompts WHERE prompt_id = :id")
                .setParameter("id", saved.getPromptId())
                .getSingleResult();

        assertThat(column).isEqualTo("SCIENCE");
        assertThat(writingPromptRepository.findById(saved.getPromptId()).orElseThrow().getTopic()).isEqualTo(Topic.SCIENCE);
    }

    @Test
    @DisplayName("writing: a draft prompt is not offered to learners")
    void writing() {
        WritingPrompt published = entityManager.persistAndFlush(prompt(ContentStatus.PUBLISHED));
        WritingPrompt draft = entityManager.persistAndFlush(prompt(ContentStatus.DRAFT));

        assertThat(writingPromptRepository.findPublishedOrderByCreatedAtDesc())
                .extracting(WritingPrompt::getPromptId).contains(published.getPromptId())
                .doesNotContain(draft.getPromptId());
        assertThat(writingPromptRepository.findPublishedByEssayType(EssayType.OPINION))
                .extracting(WritingPrompt::getPromptId).contains(published.getPromptId())
                .doesNotContain(draft.getPromptId());
        assertThat(writingPromptRepository.findAllByOrderByCreatedAtDesc())
                .extracting(WritingPrompt::getPromptId).contains(published.getPromptId(), draft.getPromptId());
    }

    @Test
    @DisplayName("mock tests: a draft paper is not listed for learners")
    void mockTests() {
        MockTest published = entityManager.persistAndFlush(paper(ContentStatus.PUBLISHED));
        MockTest draft = entityManager.persistAndFlush(paper(ContentStatus.DRAFT));

        assertThat(mockTestRepository.findAllPublished())
                .extracting(MockTest::getMockTestId).contains(published.getMockTestId())
                .doesNotContain(draft.getMockTestId());
        assertThat(mockTestRepository.findAll())
                .extracting(MockTest::getMockTestId).contains(published.getMockTestId(), draft.getMockTestId());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private ReadingQuiz quiz(ContentStatus status) {
        return ReadingQuiz.builder().user(author).topic(Topic.SCIENCE).difficulty(Difficulty.PASSAGE_3)
                .passageText(MARK).isTemplate(true).contentStatus(status).build();
    }

    private ListeningPart part(ContentStatus status) {
        return ListeningPart.builder().partNumber(4).title(MARK).audioUrl("/x.mp3")
                .contentStatus(status).build();
    }

    private WritingPrompt prompt(ContentStatus status) {
        return WritingPrompt.builder().promptText(MARK).essayType(EssayType.OPINION)
                .taskType(WritingTaskType.TASK_2).contentStatus(status).build();
    }

    private ListeningPart bankPart(ContentStatus status, AudioStatus audioStatus, String topic) {
        return ListeningPart.builder().partNumber(2).title(MARK).topic(topic).audioUrl("/x.mp3")
                .audioStatus(audioStatus).contentStatus(status).build();
    }

    private WritingPrompt bankPrompt(ContentStatus status, EssayType type, Topic topic) {
        return WritingPrompt.builder().promptText(MARK).essayType(type)
                .taskType(type.isTask1() ? WritingTaskType.TASK_1 : WritingTaskType.TASK_2)
                .topic(topic).contentStatus(status).build();
    }

    private MockTest paper(ContentStatus status) {
        return MockTest.builder().title(MARK).difficulty(MockTestDifficulty.MEDIUM)
                .contentStatus(status).build();
    }
}
