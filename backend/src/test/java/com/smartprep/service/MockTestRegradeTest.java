package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.*;
import com.smartprep.service.ai.MockTestAsyncGrader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recovery path for a mock test whose AI writing evaluation failed.
 * <p>
 * A transient Gemini outage left a submission in FAILED permanently: listening and reading
 * were already scored and saved, but the writing band could never be produced and there was
 * no way to ask for another attempt. The essays are not stored on the submission, so the
 * retry reads them back from the session's progress JSON.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockTestRegradeTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long SUBMISSION_ID = 50L;
    private static final Long SESSION_ID = 60L;

    @Mock private MockTestRepository mockTestRepository;
    @Mock private MockTestSessionRepository sessionRepository;
    @Mock private MockTestSubmissionRepository submissionRepository;
    @Mock private ListeningTestRepository listeningTestRepository;
    @Mock private UserRepository userRepository;
    @Mock private MockTestAsyncGrader asyncGrader;
    @Mock private ExamDurationConfig durationConfig;

    // A real mapper, not a mock: the point is that the stored answers are genuinely parsed
    // back out. @Spy so @InjectMocks actually supplies it — a plain field is ignored.
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private MockTestService mockTestService;

    private MockTestSubmission submission(SubmissionStatus status, Long ownerId) {
        return MockTestSubmission.builder()
                .submissionId(SUBMISSION_ID)
                .user(User.builder().userId(ownerId).build())
                .sessionId(SESSION_ID)
                .status(status)
                .build();
    }

    private MockTestSession sessionWithEssays() {
        return MockTestSession.builder()
                .sessionId(SESSION_ID)
                .progressJson("{\"w_task1\":\"chart essay\",\"w_task2\":\"opinion essay\"}")
                .build();
    }

    @Test
    @DisplayName("re-queues grading and restores the essays from the session")
    void regrade_failedSubmission_requeuesWithStoredEssays() {
        MockTestSubmission sub = submission(SubmissionStatus.FAILED, OWNER_ID);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessionWithEssays()));

        mockTestService.regradeWriting(OWNER_ID, SUBMISSION_ID);

        assertEquals(SubmissionStatus.GRADING, sub.getStatus());
        verify(asyncGrader).gradeWritingSubmissionsAsync(
                eq(SUBMISSION_ID), eq("chart essay"), eq("opinion essay"));
    }

    @Test
    @DisplayName("refuses to re-grade a submission that already completed")
    void regrade_completedSubmission_isRejected() {
        when(submissionRepository.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.COMPLETED, OWNER_ID)));

        assertThrows(IllegalStateException.class,
                () -> mockTestService.regradeWriting(OWNER_ID, SUBMISSION_ID));

        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }

    @Test
    @DisplayName("refuses to re-grade another user's submission, as not found")
    void regrade_otherUsersSubmission_isRefused() {
        when(submissionRepository.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.FAILED, OWNER_ID)));

        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.regradeWriting(ATTACKER_ID, SUBMISSION_ID));

        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }

    @Test
    @DisplayName("reports a clear error when the session holding the essays is gone")
    void regrade_missingSession_reportsClearly() {
        when(submissionRepository.findById(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(SubmissionStatus.FAILED, OWNER_ID)));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        // mock_test_submissions.session_id has no foreign key, so the session can genuinely
        // disappear; the user must be told why rather than seeing a generic failure.
        assertThrows(ResourceNotFoundException.class,
                () -> mockTestService.regradeWriting(OWNER_ID, SUBMISSION_ID));

        verify(asyncGrader, never()).gradeWritingSubmissionsAsync(any(), any(), any());
    }
}
