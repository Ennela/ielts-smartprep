package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.dto.response.MockTestSessionResponse;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.*;
import com.smartprep.service.ai.MockTestAsyncGrader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Section ordering, resume timing and what a late request is allowed to change.
 *
 * <p>Complements {@code MockTestTimerEnforcementTest}, which covers what {@code saveProgress}
 * refuses to take from the client. The cases here are the ones where the server was still
 * trusting the request: the answers attached to a late submit were graded even though the
 * session had deliberately kept its earlier progress, and a resumed session was handed back
 * the time remaining at its last autosave rather than the time actually left.
 */
@ExtendWith(MockitoExtension.class)
class MockTestSectionFlowTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final int SECTION_SECONDS = 1800;

    @Mock private MockTestRepository mockTestRepository;
    @Mock private MockTestSessionRepository sessionRepository;
    @Mock private MockTestSubmissionRepository submissionRepository;
    @Mock private ListeningTestRepository listeningTestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private MockTestAsyncGrader asyncGrader;
    @Mock private ExamDurationConfig durationConfig;

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
                .title("Section flow fixture")
                .sections(sections)
                .listeningParts(new ArrayList<>())
                .readingQuizzes(new ArrayList<>())
                .writingPrompts(new ArrayList<>())
                .build();
    }

    private MockTestSession sessionStartedSecondsAgo(long secondsAgo, SkillType currentSection) {
        LocalDateTime start = LocalDateTime.now().minusSeconds(secondsAgo);
        return MockTestSession.builder()
                .sessionId(SESSION_ID)
                .user(User.builder().userId(USER_ID).build())
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(currentSection)
                .startedAt(start)
                .sectionStartedAt(start)
                // Deliberately stale, the way an abandoned session's column is: written at the
                // last autosave and never touched again.
                .timeRemainingSeconds(SECTION_SECONDS)
                .progressJson("{\"1\":\"saved-in-time\"}")
                .build();
    }

    // ── Resume timing ────────────────────────────────────────────────────────

    @Test
    @DisplayName("resuming a session reports the time actually left, not the stored value")
    void getSession_reportsServerComputedTime() {
        MockTestSession session = sessionStartedSecondsAgo(600, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestSessionResponse response = mockTestService.getSessionById(USER_ID, SESSION_ID);

        assertNotEquals(SECTION_SECONDS, response.getTimeRemainingSeconds(),
                "the stored column was returned verbatim, so leaving and coming back "
                        + "restored time that had already been spent");
        assertTrue(response.getTimeRemainingSeconds() <= 1200
                        && response.getTimeRemainingSeconds() > 1180,
                "expected about 1200s left, got " + response.getTimeRemainingSeconds());
    }

    @Test
    @DisplayName("a session resumed long after its deadline has no time left")
    void getSession_afterDeadlineReportsZero() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 86_400, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertEquals(0, mockTestService.getSessionById(USER_ID, SESSION_ID).getTimeRemainingSeconds());
    }

    // ── Section ordering ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sections advance Listening to Reading to Writing, and no further")
    void nextSection_followsTheServerOrder() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");

        mockTestService.nextSection(USER_ID, SESSION_ID, request);
        assertEquals(SkillType.READING, session.getCurrentSection());

        mockTestService.nextSection(USER_ID, SESSION_ID, request);
        assertEquals(SkillType.WRITING, session.getCurrentSection());

        // Writing is the last section; finishing it is a submit, not an advance.
        assertThrows(IllegalStateException.class,
                () -> mockTestService.nextSection(USER_ID, SESSION_ID, request));
        assertEquals(SkillType.WRITING, session.getCurrentSection());
    }

    @Test
    @DisplayName("advancing restarts the clock for the section being entered")
    void nextSection_restartsTheSectionClock() {
        MockTestSession session = sessionStartedSecondsAgo(1700, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");

        MockTestSessionResponse response = mockTestService.nextSection(USER_ID, SESSION_ID, request);

        // Reading gets its own full section, not what was left of Listening.
        assertTrue(response.getTimeRemainingSeconds() > SECTION_SECONDS - 30,
                "expected a fresh section clock, got " + response.getTimeRemainingSeconds());
    }

    @Test
    @DisplayName("a section cannot be advanced once the session is no longer in progress")
    void nextSection_onSubmittedSessionIsRejected() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.LISTENING);
        session.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");

        assertThrows(IllegalStateException.class,
                () -> mockTestService.nextSection(USER_ID, SESSION_ID, request));
    }

    // ── Late requests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("advancing after the deadline is refused outright, not merely sanitised")
    void nextSection_pastDeadlineIsRefused() {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 600, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{\"1\":\"answered-after-the-bell\"}");

        // This expectation is deliberately the opposite of what it was. A late advance used
        // to succeed on the last in-time progress, which discarded the late answers but still
        // handed over a fresh clock for the next section -- so abandoning an exam cost
        // nothing. The abandoned-session policy now retires the attempt instead; see
        // MockTestLifecycleTest for the full lifecycle.
        assertThrows(IllegalStateException.class,
                () -> mockTestService.nextSection(USER_ID, SESSION_ID, request));

        assertEquals("{\"1\":\"saved-in-time\"}", session.getProgressJson());
        assertEquals(SkillType.LISTENING, session.getCurrentSection());
    }

    @Test
    @DisplayName("a resubmitted exam is rejected")
    void submitExam_onAlreadySubmittedSessionIsRejected() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.WRITING);
        session.setStatus(SessionStatus.SUBMITTED);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestSubmitRequest request = new MockTestSubmitRequest();
        request.setProgressJson("{}");

        assertThrows(IllegalStateException.class,
                () -> mockTestService.submitExam(USER_ID, SESSION_ID, request));
    }

    @Test
    @DisplayName("a late submit grades the answers saved in time, not the ones posted late")
    void submitExam_pastDeadlineGradesTheProgressSavedInTime() throws Exception {
        MockTestSession session = sessionStartedSecondsAgo(SECTION_SECONDS + 600, SkillType.WRITING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));
        when(listeningTestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(submissionRepository.save(any(MockTestSubmission.class))).thenAnswer(i -> i.getArgument(0));
        // Nothing here depends on how the answers parse; what matters is which JSON is read.
        lenient().when(objectMapper.readValue(any(String.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new java.util.HashMap<String, String>());

        MockTestSubmitRequest request = new MockTestSubmitRequest();
        request.setProgressJson("{\"1\":\"answered-after-the-bell\"}");

        mockTestService.submitExam(USER_ID, SESSION_ID, request);

        // The session keeps what was saved in time...
        assertEquals("{\"1\":\"saved-in-time\"}", session.getProgressJson());
        // ...and that is what scoring read. Grading the request payload instead was what made
        // the late-submission guard decorative: the answers were still counted.
        org.mockito.Mockito.verify(objectMapper)
                .readValue(org.mockito.ArgumentMatchers.eq("{\"1\":\"saved-in-time\"}"),
                        any(com.fasterxml.jackson.core.type.TypeReference.class));
    }
}
