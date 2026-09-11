package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.response.MockTestSessionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.*;
import com.smartprep.service.ai.MockTestAsyncGrader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The abandoned-session lifecycle, and recovery from a grading run that died.
 *
 * <p>Sections are timed individually — the schema says so, with a duration per row in
 * {@code mock_test_sections} and a {@code section_started_at} on the session — and entering a
 * section starts its clock. That made walking away from an exam free: come back the next day,
 * the abandoned section reported no time left, the client advanced, and the next section
 * handed over a full fresh hour. The timer only ever bound candidates who stayed.
 *
 * <p>So a session that runs out of time on a section it never finished is now retired.
 * Submitting is still allowed past the deadline, because it grades the answers saved in time
 * and confers no advantage; continuing is not, because it does.
 */
@ExtendWith(MockitoExtension.class)
class MockTestLifecycleTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long SUBMISSION_ID = 20L;
    private static final int SECTION_SECONDS = 1800;

    @Mock private MockTestRepository mockTestRepository;
    @Mock private MockTestSessionRepository sessionRepository;
    @Mock private MockTestSubmissionRepository submissionRepository;
    @Mock private ListeningTestRepository listeningTestRepository;
    @Mock private UserRepository userRepository;
    @Mock private MockTestAsyncGrader asyncGrader;
    @Mock private ExamDurationConfig durationConfig;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private MockTestService mockTestService;

    private MockTest mockTest;

    @BeforeEach
    void setUp() {
        List<MockTestSection> sections = List.of(
                MockTestSection.builder().sectionType(SkillType.LISTENING)
                        .durationSeconds(SECTION_SECONDS).sectionOrder(1).build(),
                MockTestSection.builder().sectionType(SkillType.READING)
                        .durationSeconds(SECTION_SECONDS).sectionOrder(2).build(),
                MockTestSection.builder().sectionType(SkillType.WRITING)
                        .durationSeconds(SECTION_SECONDS).sectionOrder(3).build());

        mockTest = MockTest.builder()
                .mockTestId(1L)
                .title("Lifecycle fixture")
                .sections(sections)
                .listeningParts(new ArrayList<>())
                .readingQuizzes(new ArrayList<>())
                .writingPrompts(new ArrayList<>())
                .build();
    }

    private MockTestSession sessionStartedSecondsAgo(long secondsAgo, SkillType section) {
        LocalDateTime start = LocalDateTime.now().minusSeconds(secondsAgo);
        return MockTestSession.builder()
                .sessionId(SESSION_ID)
                .user(User.builder().userId(USER_ID).build())
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(section)
                .startedAt(start)
                .sectionStartedAt(start)
                .timeRemainingSeconds(SECTION_SECONDS)
                .progressJson("{\"1\":\"saved-in-time\"}")
                .build();
    }

    // ── A. Closing the tab does not buy time ─────────────────────────────────

    @Test
    @DisplayName("resuming mid-section returns the time actually left, not a fresh clock")
    void resumeWithinSection_doesNotResetTheTimer() {
        MockTestSession session = sessionStartedSecondsAgo(600, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestSessionResponse first = mockTestService.getSessionById(USER_ID, SESSION_ID);
        MockTestSessionResponse second = mockTestService.getSessionById(USER_ID, SESSION_ID);

        assertTrue(first.getTimeRemainingSeconds() <= 1200, "expected ~1200s, got " + first.getTimeRemainingSeconds());
        assertTrue(second.getTimeRemainingSeconds() <= first.getTimeRemainingSeconds(),
                "reloading must never hand back more time than the reload before it");
        assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
    }

    // ── B/C. Deadline exceeded is a terminal state ───────────────────────────

    @Test
    @DisplayName("a session abandoned past its section deadline is retired on read")
    void readingAnAbandonedSession_expiresIt() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 86_400, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestSessionResponse response = mockTestService.getSessionById(USER_ID, SESSION_ID);

        assertEquals(SessionStatus.EXPIRED, response.getStatus());
        assertEquals(0, response.getTimeRemainingSeconds());
    }

    @Test
    @DisplayName("an abandoned session is not offered as the active one")
    void getActiveSession_skipsAnAbandonedSession() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 86_400, SkillType.LISTENING);
        when(sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(
                USER_ID, SessionStatus.IN_PROGRESS)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.getActiveSession(USER_ID));
        assertEquals(SessionStatus.EXPIRED, session.getStatus());
    }

    @Test
    @DisplayName("an expired session cannot be continued into the next section")
    void nextSection_onAbandonedSession_isRefusedAndRetires() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 86_400, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");

        assertThrows(IllegalStateException.class,
                () -> mockTestService.nextSection(USER_ID, SESSION_ID, request));

        // This is the integrity property: no fresh Reading clock for walking away.
        assertEquals(SkillType.LISTENING, session.getCurrentSection());
        assertEquals(SessionStatus.EXPIRED, session.getStatus());
    }

    @Test
    @DisplayName("an expired session cannot be brought back to life")
    void expiredSession_cannotBecomeActiveAgain() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.LISTENING);
        session.setStatus(SessionStatus.EXPIRED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{\"1\":\"late\"}");

        assertThrows(IllegalStateException.class,
                () -> mockTestService.saveProgress(USER_ID, SESSION_ID, request));
        assertThrows(IllegalStateException.class,
                () -> mockTestService.nextSection(USER_ID, SESSION_ID, request));
        assertEquals(SessionStatus.EXPIRED, session.getStatus());
    }

    @Test
    @DisplayName("the final section is exempt, so a candidate's work can still be collected")
    void writingSessionPastDeadline_staysSubmittable() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 600, SkillType.WRITING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestSessionResponse response = mockTestService.getSessionById(USER_ID, SESSION_ID);

        // Expiring here would destroy work and protect nothing: there is no later section
        // whose clock could be gained.
        assertEquals(SessionStatus.IN_PROGRESS, response.getStatus());
    }

    // ── E. Late answers never reach scoring ──────────────────────────────────

    @Test
    @DisplayName("autosaving after the deadline does not smuggle answers into the graded set")
    void saveProgress_pastDeadline_doesNotAcceptAnswers() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 600, SkillType.WRITING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{\"1\":\"answered-after-the-bell\"}");

        mockTestService.saveProgress(USER_ID, SESSION_ID, request);

        // submitExam grades session.progressJson, so accepting this would have been graded on
        // the next submit -- guarding only the submit path left autosave as the way round it.
        assertEquals("{\"1\":\"saved-in-time\"}", session.getProgressJson());
        assertEquals(0, session.getTimeRemainingSeconds());
    }

    // ── F/G. Grading that died can be recovered, exactly once ────────────────

    private MockTestSubmission submission(SubmissionStatus status, LocalDateTime submittedAt) {
        return MockTestSubmission.builder()
                .submissionId(SUBMISSION_ID)
                .user(User.builder().userId(USER_ID).build())
                .sessionId(SESSION_ID)
                .status(status)
                .submittedAt(submittedAt)
                .build();
    }

    @Test
    @DisplayName("a grading run that died leaves a submission that can be retried")
    void strandedGrading_canBeRecovered() {
        MockTestSubmission sub = submission(SubmissionStatus.GRADING, LocalDateTime.now().minusHours(2));
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.claimForRegrade(eq(SUBMISSION_ID), any(), any(), any())).thenReturn(1);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(MockTestSession.builder()
                .sessionId(SESSION_ID)
                .progressJson("{\"w_task1\":\"chart essay\",\"w_task2\":\"opinion essay\"}")
                .build()));

        mockTestService.regradeWriting(USER_ID, SUBMISSION_ID);

        verify(asyncGrader).gradeWritingSubmissionsAsync(
                eq(SUBMISSION_ID), eq("chart essay"), eq("opinion essay"));
    }

    @Test
    @DisplayName("a retry that loses the claim does not queue a second grading run")
    void losingTheClaim_doesNotDoubleGrade() {
        MockTestSubmission sub = submission(SubmissionStatus.GRADING, LocalDateTime.now().minusHours(2));
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        // Another retry got there first: the conditional UPDATE matched nothing.
        when(submissionRepository.claimForRegrade(eq(SUBMISSION_ID), any(), any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> mockTestService.regradeWriting(USER_ID, SUBMISSION_ID));

        // Two sets of essay rows for one sitting is the thing being prevented.
        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }

    @Test
    @DisplayName("recovering another user's stranded grading is refused as not found")
    void strandedGrading_isStillOwnershipChecked() {
        MockTestSubmission sub = submission(SubmissionStatus.GRADING, LocalDateTime.now().minusHours(2));
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.regradeWriting(999L, SUBMISSION_ID));

        verify(submissionRepository, never()).claimForRegrade(any(), any(), any(), any());
        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }
}
