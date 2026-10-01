package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingSubmitFullRequest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards against a full Reading test being submitted twice.
 *
 * <p>There was no guard at all. Every submit re-graded the same quizzes and overwrote their
 * scores, and the response lists every correct answer — so a blank submit followed by a
 * second one scored 9.0 and erased the evidence of the first.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingFullResubmitGuardTest {

    private static final Long USER_ID = 7L;

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ReadingQueryService readingQueryService;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks private ReadingGradingService readingGradingService;

    @BeforeEach
    void setUp() {
        // submitFullQuiz loads the user before it reaches the guard.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
    }

    private static ReadingQuiz quiz(Long id, LocalDateTime submittedAt) {
        return ReadingQuiz.builder().quizId(id).submittedAt(submittedAt).build();
    }

    private static ReadingSubmitFullRequest request(List<Long> quizIds) {
        ReadingSubmitFullRequest request = new ReadingSubmitFullRequest();
        request.setQuizIds(quizIds);
        request.setAnswers(Map.of(1L, "A"));
        return request;
    }

    @Test
    @DisplayName("refuses a full test in which any passage was already graded")
    void alreadyGraded_isRejected() {
        when(quizRepository.findByQuizIdAndUserUserId(1L, USER_ID)).thenReturn(Optional.of(quiz(1L, null)));
        when(quizRepository.findByQuizIdAndUserUserId(2L, USER_ID))
                .thenReturn(Optional.of(quiz(2L, LocalDateTime.now().minusMinutes(5))));

        assertThatThrownBy(() -> readingGradingService.submitFullQuiz(USER_ID, request(List.of(1L, 2L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been submitted");

        // Nothing was claimed, graded or written.
        verify(quizRepository, never()).claimForSubmission(any(), any(), any());
        verify(quizRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("refuses when a concurrent submit claimed the quizzes first")
    void lostTheRace_isRejected() {
        when(quizRepository.findByQuizIdAndUserUserId(1L, USER_ID)).thenReturn(Optional.of(quiz(1L, null)));
        when(quizRepository.findByQuizIdAndUserUserId(2L, USER_ID)).thenReturn(Optional.of(quiz(2L, null)));
        // Both looked unsubmitted, but by the time this request claimed them only one was left.
        when(quizRepository.claimForSubmission(any(), eq(USER_ID), any())).thenReturn(1);

        assertThatThrownBy(() -> readingGradingService.submitFullQuiz(USER_ID, request(List.of(1L, 2L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been submitted");

        verify(quizRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("claims each quiz once even when the request names it twice")
    void duplicateIds_areClaimedOnce() {
        when(quizRepository.findByQuizIdAndUserUserId(1L, USER_ID)).thenReturn(Optional.of(quiz(1L, null)));
        // Returning 0 stops the submit before grading, which this test does not need. What it
        // asserts is the argument: claiming [1, 1] changes one row, so a service that did not
        // de-duplicate would compare 1 with 2 and refuse an ordinary submit.
        when(quizRepository.claimForSubmission(any(), eq(USER_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> readingGradingService.submitFullQuiz(USER_ID, request(List.of(1L, 1L))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(quizRepository).claimForSubmission(eq(List.of(1L)), eq(USER_ID), any());
    }
}
