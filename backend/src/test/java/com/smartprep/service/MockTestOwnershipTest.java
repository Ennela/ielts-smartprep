package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.*;
import com.smartprep.service.ai.MockTestAsyncGrader;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * A mock test session belonging to someone else must be indistinguishable from one that
 * does not exist.
 * <p>
 * These ownership checks used to raise a bare {@code SecurityException}, which no handler
 * covered. It fell through to the catch-all and returned HTTP 500 while capturing the
 * exception in Sentry. That gave an attacker an existence oracle — 500 meant "this id
 * exists but is not yours", 404 meant "no such id" — and buried real server errors under
 * routine authorization noise.
 */
@ExtendWith(MockitoExtension.class)
class MockTestOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long SESSION_ID = 77L;
    private static final Long MISSING_SESSION_ID = 78L;

    @Mock private MockTestRepository mockTestRepository;
    @Mock private MockTestSessionRepository sessionRepository;
    @Mock private MockTestSubmissionRepository submissionRepository;
    @Mock private ListeningTestRepository listeningTestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private MockTestAsyncGrader asyncGrader;
    @Mock private ExamDurationConfig durationConfig;

    @InjectMocks private MockTestService mockTestService;

    private MockTestSession sessionOwnedByOther() {
        return MockTestSession.builder()
                .sessionId(SESSION_ID)
                .user(User.builder().userId(OWNER_ID).build())
                .mockTest(MockTest.builder()
                        .mockTestId(1L)
                        .sections(List.of())
                        .listeningParts(new ArrayList<>())
                        .readingQuizzes(new ArrayList<>())
                        .writingPrompts(new ArrayList<>())
                        .build())
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.LISTENING)
                .startedAt(LocalDateTime.now())
                .sectionStartedAt(LocalDateTime.now())
                .timeRemainingSeconds(1800)
                .progressJson("{}")
                .build();
    }

    @Test
    @DisplayName("reading another user's session is refused as not found")
    void getSessionById_otherUsersSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOwnedByOther()));

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.getSessionById(ATTACKER_ID, SESSION_ID));
    }

    @Test
    @DisplayName("saving progress on another user's session is refused as not found")
    void saveProgress_otherUsersSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOwnedByOther()));

        MockTestProgressRequest request = new MockTestProgressRequest();
        request.setProgressJson("{}");

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.saveProgress(ATTACKER_ID, SESSION_ID, request));
    }

    @Test
    @DisplayName("submitting another user's session is refused as not found")
    void submitExam_otherUsersSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOwnedByOther()));

        MockTestSubmitRequest request = new MockTestSubmitRequest();
        request.setProgressJson("{}");

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.submitExam(ATTACKER_ID, SESSION_ID, request));
    }

    /**
     * The point of the fix: a session that exists but belongs to someone else and a session
     * that does not exist must be reported identically, message included.
     */
    @Test
    @DisplayName("a foreign session and a missing session are indistinguishable")
    void foreignAndMissingSession_reportIdentically() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionOwnedByOther()));
        when(sessionRepository.findById(MISSING_SESSION_ID)).thenReturn(Optional.empty());

        ResourceNotFoundException foreign = assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.getSessionById(ATTACKER_ID, SESSION_ID));
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.getSessionById(ATTACKER_ID, MISSING_SESSION_ID));

        assertEquals(foreign.getClass(), missing.getClass());
        // Only the id differs, and the caller supplied that — nothing about existence leaks.
        assertEquals(foreign.getMessage().replace(String.valueOf(SESSION_ID), "X"),
                missing.getMessage().replace(String.valueOf(MISSING_SESSION_ID), "X"));
    }
}
