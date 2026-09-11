package com.smartprep.service;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.UserRepository;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lazy expiry against a real database and real transactions.
 *
 * <p>The unit tests for this behaviour mock the repository, and a mocked repository cannot
 * see a rollback. That is exactly how this bug got through them: {@code getActiveSession}
 * saved EXPIRED and then threw not-found so the caller would see no active session -- and a
 * RuntimeException leaving a {@code @Transactional} method rolls the transaction back. The
 * log said "expiring", the UI behaved correctly because every request re-evaluated and
 * re-threw, and the row never left IN_PROGRESS. It was found in a browser session by reading
 * the table afterwards, which is what this test does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MockTestExpiryIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockTestService mockTestService;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private UserRepository userRepository;

    // Redis consumers that would otherwise need a live server; neither is on this path.
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    private MockTestSession abandonedSessionFor(User user) {
        List<MockTest> tests = mockTestRepository.findAll();
        assertThat(tests).as("Flyway seeds at least one mock test").isNotEmpty();

        return sessionRepository.save(MockTestSession.builder()
                .user(user)
                .mockTest(tests.get(0))
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.LISTENING)
                .timeRemainingSeconds(1)
                .progressJson("{}")
                .build());
    }

    private void rewind(MockTestSession session) {
        // @PrePersist stamps "now" on the section clock, so the rewind has to happen after
        // the save. Three hours is past any section length plus the submit grace.
        session.setSectionStartedAt(LocalDateTime.now().minusHours(3));
        sessionRepository.saveAndFlush(session);
    }

    private User newUser(String tag) {
        return userRepository.save(User.builder()
                .username("expiry_" + tag)
                .passwordHash("irrelevant")
                .email("expiry_" + tag + "@example.test")
                .displayName("Expiry " + tag)
                .role(Role.STUDENT)
                .build());
    }

    @Test
    @DisplayName("getActiveSession retires an abandoned session, and the retirement survives the not-found it then throws")
    void getActiveSession_expiryIsCommittedDespiteTheThrow() {
        User user = newUser("active");
        MockTestSession session = abandonedSessionFor(user);
        rewind(session);

        assertThatThrownBy(() -> mockTestService.getActiveSession(user.getUserId()))
                .isInstanceOf(ResourceNotFoundException.class);

        // The assertion that a mocked repository could never make.
        MockTestSession reloaded = sessionRepository.findById(session.getSessionId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("the EXPIRED write must not be rolled back by the not-found that follows it")
                .isEqualTo(SessionStatus.EXPIRED);
        assertThat(reloaded.getTimeRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("getSessionById retires an abandoned session and reports it as EXPIRED")
    void getSessionById_expiryIsCommitted() {
        User user = newUser("byid");
        MockTestSession session = abandonedSessionFor(user);
        rewind(session);

        assertThat(mockTestService.getSessionById(user.getUserId(), session.getSessionId()).getStatus())
                .isEqualTo(SessionStatus.EXPIRED);

        assertThat(sessionRepository.findById(session.getSessionId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    @DisplayName("an expired session does not come back as active on the next read")
    void expiredSession_staysExpired() {
        User user = newUser("stays");
        MockTestSession session = abandonedSessionFor(user);
        rewind(session);

        assertThatThrownBy(() -> mockTestService.getActiveSession(user.getUserId()))
                .isInstanceOf(ResourceNotFoundException.class);
        // A second read must find nothing active -- the query is by status, so this only
        // holds if the first call's EXPIRED actually reached the table.
        assertThatThrownBy(() -> mockTestService.getActiveSession(user.getUserId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(
                user.getUserId(), SessionStatus.IN_PROGRESS)).isEmpty();
    }
}
