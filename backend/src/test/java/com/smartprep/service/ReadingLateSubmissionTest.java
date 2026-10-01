package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingSubmitFullRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A Reading submission that arrives after its attempt's deadline is refused before any
 * quiz is claimed or graded. The deadline used to be stored and never read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingLateSubmissionTest {

    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 70L;

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ReadingQueryService readingQueryService;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks private ReadingGradingService readingGradingService;

    @BeforeEach
    void setUp() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(quizRepository.findByQuizIdAndUserUserId(1L, USER_ID))
                .thenReturn(Optional.of(ReadingQuiz.builder().quizId(1L).build()));
        doThrow(new IllegalArgumentException("Time is up for this test, so it can no longer be submitted."))
                .when(examAttemptService).assertWithinDeadline(ATTEMPT_ID, USER_ID, SkillType.READING);
    }

    @Test
    @DisplayName("a late single passage is refused before it is claimed")
    void singleQuiz_late_refused() {
        ReadingSubmitRequest request = new ReadingSubmitRequest(Map.of(1L, "A"), ATTEMPT_ID, true);

        assertThatThrownBy(() -> readingGradingService.submitQuiz(1L, USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time is up");

        verify(quizRepository, never()).claimForSubmission(any(), any(), any());
        verify(scoreHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a late full test is refused before any passage is claimed")
    void fullTest_late_refused() {
        ReadingSubmitFullRequest request = new ReadingSubmitFullRequest(List.of(1L), Map.of(1L, "A"), ATTEMPT_ID, true);

        assertThatThrownBy(() -> readingGradingService.submitFullQuiz(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time is up");

        verify(quizRepository, never()).claimForSubmission(any(), any(), any());
        verify(scoreHistoryRepository, never()).save(any());
    }
}
