package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.ai.WritingGradingService.GradingResult;
import com.smartprep.service.ai.WritingGradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When time runs out, an essay below the minimum is marked instead of thrown away (P1 #19).
 *
 * <p>The page used to discard the draft after the server refused it, so the candidate lost
 * everything they had written. A candidate submitting by hand is still held to the minimum.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WritingTimeoutKeepsWorkTest {

    @Mock private WritingPromptRepository promptRepository;
    @Mock private WritingSubmissionRepository submissionRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private WritingGradingService writingGradingService;
    @Mock private WritingQueryService writingQueryService;
    @Mock private WritingGradingPersistence gradingPersistence;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private WritingService writingService;

    private static final String SHORT_ESSAY = "a short essay of about one hundred words";

    @BeforeEach
    void setUp() {
        WritingPrompt prompt = WritingPrompt.builder().promptId(20L).promptText("Discuss both views.")
                .essayType(EssayType.OPINION).taskType(WritingTaskType.TASK_2).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));
        when(promptRepository.findById(20L)).thenReturn(Optional.of(prompt));
        when(writingGradingService.countWords(SHORT_ESSAY)).thenReturn(100);
        when(writingGradingService.evaluateEssay(anyString(), anyString(), anyBoolean()))
                .thenReturn(GradingResult.builder().errorsJson("[]").build());
    }

    @Test
    @DisplayName("a short essay the timer submitted is marked")
    void timerSubmissionIsMarked() {
        writingService.gradeEssay(1L, new WritingGradeRequest(20L, SHORT_ESSAY, true, null));

        verify(writingGradingService).evaluateEssay(anyString(), eq(SHORT_ESSAY), eq(false));
    }

    @Test
    @DisplayName("a short essay the candidate submitted is still refused, so they can keep writing")
    void manualSubmissionStillNeedsTheMinimum() {
        assertThrows(WordCountTooLowException.class,
                () -> writingService.gradeEssay(1L, new WritingGradeRequest(20L, SHORT_ESSAY)));
    }
}
