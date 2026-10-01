package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards against a listening test being submitted twice.
 * <p>
 * {@code submitTest} creates a brand-new {@code ListeningTest} on every call instead of
 * marking an existing record submitted, so — unlike Reading — there is no {@code
 * submittedAt} flag to check. A repeat submit therefore used to write a second
 * ListeningTest and a second ScoreHistory row, duplicating the user's history and
 * skewing their progress. The exam attempt is used as the idempotency key.
 * <p>
 * Worse, the response lists every correct answer, so a second submit was an answer-key
 * oracle. attemptId used to be optional and leaving it out skipped the check entirely; it
 * is now required, and the result records it so the unique column in V51 can hold the line
 * when two submits race past the service check.
 */
@ExtendWith(MockitoExtension.class)
class ListeningResubmitGuardTest {

    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 99L;

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks private ListeningGradingService listeningGradingService;

    private ListeningSubmitRequest requestWithAttempt() {
        ListeningSubmitRequest request = new ListeningSubmitRequest();
        request.setTestMode("PRACTICE");
        request.setPartIds(List.of(1L));
        request.setAnswers(Map.of(1L, "answer"));
        request.setAttemptId(ATTEMPT_ID);
        return request;
    }

    @Test
    @DisplayName("rejects a submission whose attempt is already submitted")
    void resubmit_isRejected() {
        doThrow(new IllegalArgumentException("This test has already been submitted"))
                .when(examAttemptService).assertSubmittable(ATTEMPT_ID, USER_ID, SkillType.LISTENING);

        assertThrows(IllegalArgumentException.class,
                () -> listeningGradingService.submitTest(USER_ID, requestWithAttempt()));
    }

    @Test
    @DisplayName("writes no test or score history when the submission is rejected")
    void resubmit_persistsNothing() {
        doThrow(new IllegalArgumentException("This test has already been submitted"))
                .when(examAttemptService).assertSubmittable(ATTEMPT_ID, USER_ID, SkillType.LISTENING);

        assertThrows(IllegalArgumentException.class,
                () -> listeningGradingService.submitTest(USER_ID, requestWithAttempt()));

        // The duplicate rows are the actual damage, so assert they are never created.
        verify(testRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
        verify(examAttemptService, never())
                .completeAttemptInternal(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("rejects an attempt that is not the caller's, or is not a Listening attempt")
    void foreignAttempt_isRejected() {
        doThrow(new ResourceNotFoundException("Exam attempt not found"))
                .when(examAttemptService).assertSubmittable(ATTEMPT_ID, USER_ID, SkillType.LISTENING);

        assertThrows(ResourceNotFoundException.class,
                () -> listeningGradingService.submitTest(USER_ID, requestWithAttempt()));
        verify(testRepository, never()).save(any());
    }

    @Test
    @DisplayName("records the attempt on the result, which is what the unique column enforces")
    void result_recordsItsAttempt() throws Exception {
        ListeningPart part = ListeningPart.builder().partId(1L).partNumber(1).build();
        part.setQuestions(new ArrayList<>());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(testRepository.save(any(ListeningTest.class))).thenAnswer(i -> i.getArgument(0));

        listeningGradingService.submitTest(USER_ID, requestWithAttempt());

        ArgumentCaptor<ListeningTest> saved = ArgumentCaptor.forClass(ListeningTest.class);
        verify(testRepository).save(saved.capture());
        assertThat(saved.getValue().getAttemptId()).isEqualTo(ATTEMPT_ID);
    }
}
