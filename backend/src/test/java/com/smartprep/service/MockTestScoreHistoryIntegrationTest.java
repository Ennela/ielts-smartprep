package com.smartprep.service;

import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSession;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.ai.MockTestAsyncGrader;
import com.smartprep.service.ai.MockTestGradingPersistence;
import com.smartprep.service.ai.WritingGradingService;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole trail a mock test leaves in score_history, against the real schema: the V47
 * link column, the entity mapping under ddl-auto=validate, the derived existence query,
 * and the two write paths in sequence -- submit, then the writing grade, then the writing
 * grade again as a retry would run it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class MockTestScoreHistoryIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private MockTestService mockTestService;
    @Autowired private MockTestGradingPersistence persistence;
    @Autowired private MockTestRepository mockTestRepository;
    @Autowired private MockTestSessionRepository sessionRepository;
    @Autowired private MockTestSubmissionRepository submissionRepository;
    @Autowired private ScoreHistoryRepository scoreHistoryRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;

    private WritingGradingService.GradingResult graded(String band) {
        return WritingGradingService.GradingResult.builder()
                .overallBand(new BigDecimal(band)).taskResponse(new BigDecimal(band))
                .coherence(new BigDecimal(band)).lexical(new BigDecimal(band)).grammar(new BigDecimal(band))
                .wordCount(250).build();
    }

    @Test
    @DisplayName("a submitted and graded mock test leaves exactly one row per skill, linked to the submission")
    void oneRowPerSkill() {
        User user = userRepository.save(User.builder()
                .username("history_user").passwordHash("x").email("history_user@example.test")
                .displayName("History").role(Role.STUDENT).build());
        MockTest paper = mockTestRepository.findAll().stream()
                .filter(m -> "Cambridge IELTS 19 Test 1".equals(m.getTitle()))
                .findFirst().orElseThrow();
        int listeningQuestions = paper.getListeningParts().stream().mapToInt(p -> p.getQuestions().size()).sum();
        int readingQuestions = paper.getReadingQuizzes().stream().mapToInt(q -> q.getQuestions().size()).sum();

        MockTestSession session = sessionRepository.save(MockTestSession.builder()
                .user(user).mockTest(paper).status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.WRITING).timeRemainingSeconds(3000)
                .progressJson("{\"w_task1\":\"essay one\",\"w_task2\":\"essay two\"}")
                .build());

        MockTestSubmitRequest request = new MockTestSubmitRequest();
        request.setProgressJson(session.getProgressJson());
        Long submissionId = mockTestService.submitExam(user.getUserId(), session.getSessionId(), request).getSubmissionId();

        // --- after submit: Listening and Reading, nothing for Writing yet
        Map<SkillType, ScoreHistory> rows = rowsFor(user.getUserId());
        assertThat(rows.keySet()).containsExactlyInAnyOrder(SkillType.LISTENING, SkillType.READING);
        rows.values().forEach(row -> {
            assertThat(row.getMockTestSubmission().getSubmissionId()).isEqualTo(submissionId);
            assertThat(row.getDifficulty()).isEqualTo(MockTestService.MOCK_TEST_DIFFICULTY);
        });
        assertThat(rows.get(SkillType.LISTENING).getUserAnswers()).hasSize(listeningQuestions);
        assertThat(rows.get(SkillType.READING).getUserAnswers()).hasSize(readingQuestions);
        assertThat(scoreHistoryRepository.existsByMockTestSubmissionSubmissionIdAndSkillType(submissionId, SkillType.WRITING))
                .isFalse();

        // --- the writing grade lands
        MockTestGradingPersistence.GradingInputs inputs = persistence.loadInputs(submissionId);
        persistence.persistResults(submissionId, inputs, "essay one", "essay two", graded("5.0"), graded("6.5"));

        rows = rowsFor(user.getUserId());
        assertThat(rows.keySet()).containsExactlyInAnyOrder(SkillType.LISTENING, SkillType.READING, SkillType.WRITING);
        // (5.0 + 2 x 6.5) / 3 = 6.0, the same band the submission carries.
        assertThat(rows.get(SkillType.WRITING).getScore()).isEqualByComparingTo("6.0");
        MockTestSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.COMPLETED);
        assertThat(rows.get(SkillType.WRITING).getScore()).isEqualByComparingTo(submission.getWritingScore());

        // --- the grade runs again, as a retry would: still one Writing row
        persistence.persistResults(submissionId, inputs, "essay one", "essay two", graded("7.0"), graded("7.0"));

        List<ScoreHistory> all = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(user.getUserId());
        assertThat(all).hasSize(3);
        assertThat(all.stream().filter(r -> r.getSkillType() == SkillType.WRITING)).hasSize(1);
    }

    private Map<SkillType, ScoreHistory> rowsFor(Long userId) {
        return scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .collect(Collectors.toMap(ScoreHistory::getSkillType, Function.identity()));
    }
}
