package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Server-side enforcement of the mock test timer and section order.
 * <p>
 * {@code saveProgress} used to persist whatever {@code timeRemainingSeconds} and
 * {@code currentSection} the client posted, and {@code submitExam} only checked that the
 * session was IN_PROGRESS. Between them a caller could grant itself unlimited time or
 * submit a "completed" exam while still on the first section, with no Reading or Writing
 * answers. The deadline is derived from {@code sectionStartedAt} plus the configured
 * section length, both of which are server-owned.
 */
@ExtendWith(MockitoExtension.class)
class MockTestTimerEnforcementTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final int LISTENING_SECONDS = 1800;

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
        MockTestSection listeningSection = MockTestSection.builder()
                .sectionType(SkillType.LISTENING)
                .durationSeconds(LISTENING_SECONDS)
                .sectionOrder(1)
                .build();

        mockTest = MockTest.builder()
                .mockTestId(1L)
                .title("Timer enforcement fixture")
                .sections(List.of(listeningSection))
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
                .timeRemainingSeconds(LISTENING_SECONDS)
                .progressJson("{}")
                .build();
    }

    @Test
    @DisplayName("saveProgress ignores the time remaining claimed by the client")
    void saveProgress_ignoresClientClock() {
        MockTestSession session = sessionStartedSecondsAgo(600, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{\"1\":\"A\"}");
        request.setTimeRemainingSeconds(999_999);
        request.setCurrentSection(SkillType.WRITING);

        mockTestService.saveProgress(USER_ID, SESSION_ID, request);

        assertNotEquals(999_999, session.getTimeRemainingSeconds(),
                "client-supplied time must not be persisted");
        // 1800s section started 600s ago leaves roughly 1200s.
        assertTrue(session.getTimeRemainingSeconds() <= 1200
                        && session.getTimeRemainingSeconds() > 1180,
                "expected about 1200s left, got " + session.getTimeRemainingSeconds());
    }

    @Test
    @DisplayName("saveProgress does not let the client jump to another section")
    void saveProgress_ignoresClientSection() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");
        request.setTimeRemainingSeconds(100);
        request.setCurrentSection(SkillType.WRITING);

        mockTestService.saveProgress(USER_ID, SESSION_ID, request);

        assertEquals(SkillType.LISTENING, session.getCurrentSection());
    }

    @Test
    @DisplayName("saveProgress reports no time left once the section deadline has passed")
    void saveProgress_expiredSectionReportsZero() {
        MockTestSession session = sessionStartedSecondsAgo(LISTENING_SECONDS + 300, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(MockTestSession.class))).thenAnswer(i -> i.getArgument(0));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");
        request.setTimeRemainingSeconds(500);

        mockTestService.saveProgress(USER_ID, SESSION_ID, request);

        assertEquals(0, session.getTimeRemainingSeconds());
    }

    @Test
    @DisplayName("submitExam refuses to grade a test that has not reached the Writing section")
    void submitExam_beforeFinalSection_isRejected() {
        MockTestSession session = sessionStartedSecondsAgo(60, SkillType.LISTENING);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MockTestSubmitRequest request = new MockTestSubmitRequest();
        request.setProgressJson("{}");

        assertThrows(IllegalStateException.class,
                () -> mockTestService.submitExam(USER_ID, SESSION_ID, request));

        // Nothing may be persisted or queued for AI grading.
        assertEquals(SessionStatus.IN_PROGRESS, session.getStatus());
        verify(submissionRepository, never()).save(any());
        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }
}
