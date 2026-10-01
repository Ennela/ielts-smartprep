package com.smartprep.repository;

import com.smartprep.model.entity.ExamAttempt;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.TestMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database half of the Listening resubmission fix (V51).
 *
 * <p>The service refuses a second submit for a graded attempt, but two submits arriving
 * together can both pass that check before either commits. This constraint is what lets
 * only one of them write a result. It has to be tested against MySQL, because the thing
 * under test is MySQL's behaviour, not the application's.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ListeningTestAttemptConstraintTest extends AbstractMySQLContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(User.builder()
                .username("listening_constraint_user")
                .email("listening-constraint@test.com")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build());
        LocalDateTime now = LocalDateTime.now();
        attempt = entityManager.persistAndFlush(ExamAttempt.builder()
                .user(user)
                .skillType(SkillType.LISTENING)
                .durationSeconds(1800)
                .startedAt(now)
                .deadline(now.plusMinutes(30))
                .build());
    }

    private ListeningTest resultFor(Long attemptId) {
        return ListeningTest.builder()
                .user(user)
                .testMode(TestMode.PRACTICE)
                .attemptId(attemptId)
                .build();
    }

    @Test
    @DisplayName("a second result for the same attempt is refused by the database")
    void secondResultForTheSameAttempt_isRejected() {
        entityManager.persistAndFlush(resultFor(attempt.getAttemptId()));

        assertThatThrownBy(() -> entityManager.persistAndFlush(resultFor(attempt.getAttemptId())))
                .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class)
                .rootCause()
                .hasMessageContaining("uq_listening_tests_attempt");
    }

    @Test
    @DisplayName("results written before V51, with no attempt, do not collide with each other")
    void legacyResultsWithoutAnAttempt_areAllowed() {
        assertThatCode(() -> {
            entityManager.persistAndFlush(resultFor(null));
            entityManager.persistAndFlush(resultFor(null));
        }).doesNotThrowAnyException();
    }
}
