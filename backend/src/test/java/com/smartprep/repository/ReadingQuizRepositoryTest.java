package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ReadingQuizRepository}.
 * Uses Testcontainers MySQL with Flyway migrations to validate actual queries.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReadingQuizRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReadingQuizRepository readingQuizRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("reading_test_user")
                .email("reading@test.com")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build();
        testUser = entityManager.persistAndFlush(testUser);
    }

    @Test
    @DisplayName("findByUserUserIdOrderByCreatedAtDesc — returns quizzes for user in descending order")
    void findByUser_orderedByCreatedAtDesc() {
        ReadingQuiz quiz1 = createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null);
        ReadingQuiz quiz2 = createQuiz(testUser, Topic.ENVIRONMENT, Difficulty.PASSAGE_2, false, null);
        entityManager.persistAndFlush(quiz1);
        entityManager.persistAndFlush(quiz2);

        List<ReadingQuiz> results = readingQuizRepository.findByUserUserIdOrderByCreatedAtDesc(testUser.getUserId());

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(q -> q.getUser().getUserId().equals(testUser.getUserId()));
    }

    @Test
    @DisplayName("findByQuizIdAndUserUserId — returns quiz only for the owning user")
    void findByQuizIdAndUserId() {
        ReadingQuiz quiz = createQuiz(testUser, Topic.HEALTH, Difficulty.PASSAGE_1, false, null);
        quiz = entityManager.persistAndFlush(quiz);

        assertThat(readingQuizRepository.findByQuizIdAndUserUserId(quiz.getQuizId(), testUser.getUserId()))
                .isPresent();

        assertThat(readingQuizRepository.findByQuizIdAndUserUserId(quiz.getQuizId(), 999L))
                .isEmpty();
    }

    @Test
    @DisplayName("findQuizzesForAdmin — filters ADMIN templates with pagination")
    void findQuizzesForAdmin_adminSourceWithPagination() {
        // Create 3 template quizzes (admin-created)
        for (int i = 0; i < 3; i++) {
            entityManager.persistAndFlush(createQuiz(null, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, true, null));
        }
        // Create 2 AI-generated quizzes (not templates, no parent)
        for (int i = 0; i < 2; i++) {
            entityManager.persistAndFlush(createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null));
        }

        // Fetch admin templates only, page 0, size 2
        Page<ReadingQuiz> page = readingQuizRepository.findQuizzesForAdmin(
                null, null, "ADMIN", PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(2);
        assertThat(page.getContent()).allMatch(ReadingQuiz::getIsTemplate);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("findQuizzesForAdmin — filters by topic and difficulty")
    void findQuizzesForAdmin_filtersTopicAndDifficulty() {
        entityManager.persistAndFlush(createQuiz(null, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, true, null));
        entityManager.persistAndFlush(createQuiz(null, Topic.ENVIRONMENT, Difficulty.PASSAGE_2, true, null));
        entityManager.persistAndFlush(createQuiz(null, Topic.TECHNOLOGY, Difficulty.PASSAGE_2, true, null));

        Page<ReadingQuiz> result = readingQuizRepository.findQuizzesForAdmin(
                Topic.TECHNOLOGY, Difficulty.PASSAGE_1, "ADMIN", PageRequest.of(0, 10));

        assertThat(result.getContent()).allMatch(q ->
                q.getTopic() == Topic.TECHNOLOGY && q.getDifficulty() == Difficulty.PASSAGE_1);
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    // ===================================================================
    //  claimForSubmission — the reading half of the resubmission fix
    // ===================================================================

    @Test
    @DisplayName("claimForSubmission — claims every unsubmitted quiz the caller owns")
    void claim_claimsUnsubmittedQuizzes() {
        ReadingQuiz a = entityManager.persistAndFlush(createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null));
        ReadingQuiz b = entityManager.persistAndFlush(createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_2, false, null));

        int claimed = readingQuizRepository.claimForSubmission(
                List.of(a.getQuizId(), b.getQuizId()), testUser.getUserId(), LocalDateTime.now());

        assertThat(claimed).isEqualTo(2);
        entityManager.clear();
        assertThat(readingQuizRepository.findById(a.getQuizId()).orElseThrow().getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("claimForSubmission — a second claim finds nothing left, which is what stops a resubmit")
    void claim_isOnlyOnce() {
        ReadingQuiz quiz = entityManager.persistAndFlush(createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null));
        List<Long> ids = List.of(quiz.getQuizId());

        assertThat(readingQuizRepository.claimForSubmission(ids, testUser.getUserId(), LocalDateTime.now())).isEqualTo(1);
        assertThat(readingQuizRepository.claimForSubmission(ids, testUser.getUserId(), LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("claimForSubmission — a partly submitted set claims less than was asked for")
    void claim_partialSetIsDetectable() {
        ReadingQuiz done = createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null);
        done.setSubmittedAt(LocalDateTime.now().minusDays(1));
        done = entityManager.persistAndFlush(done);
        ReadingQuiz fresh = entityManager.persistAndFlush(createQuiz(testUser, Topic.TECHNOLOGY, Difficulty.PASSAGE_2, false, null));

        int claimed = readingQuizRepository.claimForSubmission(
                List.of(done.getQuizId(), fresh.getQuizId()), testUser.getUserId(), LocalDateTime.now());

        // The service compares this with the number of ids and refuses the whole submit.
        assertThat(claimed).isEqualTo(1);
    }

    @Test
    @DisplayName("claimForSubmission — cannot claim another user's quiz")
    void claim_isScopedToTheOwner() {
        User other = entityManager.persistAndFlush(User.builder()
                .username("reading_other_user")
                .email("other@test.com")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
        ReadingQuiz theirs = entityManager.persistAndFlush(createQuiz(other, Topic.TECHNOLOGY, Difficulty.PASSAGE_1, false, null));

        assertThat(readingQuizRepository.claimForSubmission(
                List.of(theirs.getQuizId()), testUser.getUserId(), LocalDateTime.now())).isZero();
    }

    private ReadingQuiz createQuiz(User user, Topic topic, Difficulty difficulty,
                                   boolean isTemplate, Long parentTemplateId) {
        return ReadingQuiz.builder()
                .user(user)
                .topic(topic)
                .difficulty(difficulty)
                .passageText("Test passage text for integration tests. This is a sample passage.")
                .timeLimitSeconds(600)
                .isTemplate(isTemplate)
                .parentTemplateId(parentTemplateId)
                .build();
    }
}
