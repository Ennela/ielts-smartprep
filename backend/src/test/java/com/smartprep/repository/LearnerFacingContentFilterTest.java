package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.MockTestDifficulty;
import com.smartprep.model.enums.Role;
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

    private MockTest paper(ContentStatus status) {
        return MockTest.builder().title(MARK).difficulty(MockTestDifficulty.MEDIUM)
                .contentStatus(status).build();
    }
}
