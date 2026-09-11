package com.smartprep.service;

import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * The async grader must be handed a submission it can actually see.
 *
 * <p>{@code regradeWriting} (and {@code submitExam}) used to call the grader from inside
 * their own transaction. The grader runs on another thread and opens its own transaction,
 * so its {@code findById} raced the commit: when the executor won, the row was not there
 * yet, the grader logged "not found" and aborted, and the submission sat in GRADING with
 * nothing left to finish it. Found in a browser session, where a real submission produced
 * exactly that log line.
 *
 * <p>The stubbed grader here reads the submission through a {@code REQUIRES_NEW}
 * transaction -- a different transaction from the caller's, the way the real async thread
 * has one. That is what makes the test deterministic instead of a race: before the fix the
 * read happens inside the caller's transaction and cannot see the uncommitted claim, after
 * it the read happens after commit and can.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MockTestGradingDispatchIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockTestService mockTestService;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    @Test
    @DisplayName("the grader is dispatched only after the claim is committed, so it can see it")
    void graderSeesTheCommittedClaim() {
        User user = userRepository.save(User.builder()
                .username("dispatch_user").passwordHash("x").email("dispatch@example.test")
                .displayName("Dispatch").role(Role.STUDENT).build());
        MockTest test = mockTestRepository.findAll().get(0);

        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(user).mockTest(test).status(SessionStatus.SUBMITTED)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(0)
                .progressJson("{\"w_task1\":\"essay one\",\"w_task2\":\"essay two\"}")
                .build());

        MockTestSubmission failed = submissionRepository.save(MockTestSubmission.builder()
                .user(user).mockTest(test).sessionId(session.getSessionId())
                .status(SubmissionStatus.FAILED)
                .listeningScore(BigDecimal.ONE).readingScore(BigDecimal.ONE)
                .writingScore(BigDecimal.ZERO).overallBand(BigDecimal.ZERO)
                .listeningCorrectAnswers(0).readingCorrectAnswers(0)
                .build());

        // What the grader would observe from its own transaction at the moment it is called.
        AtomicReference<SubmissionStatus> seenByGrader = new AtomicReference<>();
        TransactionTemplate fresh = new TransactionTemplate(transactionManager);
        fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            seenByGrader.set(fresh.execute(status ->
                    submissionRepository.findById(id).map(MockTestSubmission::getStatus).orElse(null)));
            return null;
        }).when(asyncGrader).gradeWritingSubmissionsAsync(anyLong(), any(), any());

        mockTestService.regradeWriting(user.getUserId(), failed.getSubmissionId());

        assertThat(seenByGrader.get())
                .as("the grader was called before the claim committed: from its own transaction "
                        + "it either cannot see the row at all (null) or still sees FAILED")
                .isEqualTo(SubmissionStatus.GRADING);
    }
}
