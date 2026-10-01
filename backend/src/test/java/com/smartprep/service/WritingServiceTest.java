package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
import com.smartprep.service.ai.WritingGradingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingServiceTest {

    @Mock private WritingPromptRepository promptRepository;
    @Mock private WritingSubmissionRepository submissionRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private WritingGradingService writingGradingService;
    @Mock private WritingQueryService writingQueryService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks
    private WritingService writingService;

    @Test
    @DisplayName("Task 1 below 150 words is rejected before AI grading or persistence")
    void gradeEssay_task1TooShort_rejectsBeforeAiAndPersistence() {
        User user = User.builder().userId(1L).build();
        WritingPrompt prompt = WritingPrompt.builder()
                .promptId(10L)
                .promptText("Describe the chart.")
                .essayType(EssayType.LINE_GRAPH)
                .taskType(WritingTaskType.TASK_1)
                .build();
        WritingGradeRequest request = new WritingGradeRequest(10L, "short task one essay");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptRepository.findById(10L)).thenReturn(Optional.of(prompt));
        when(writingGradingService.countWords(request.getEssayText())).thenReturn(149);

        WordCountTooLowException ex = assertThrows(
                WordCountTooLowException.class,
                () -> writingService.gradeEssay(1L, request));

        assertTrue(ex.getMessage().contains("Task 1"));
        verify(writingGradingService, never()).evaluateEssay(any(), any(), anyBoolean());
        verify(submissionRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
        verify(writingQueryService, never()).buildGradeResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Task 2 below 250 words is rejected before AI grading or persistence")
    void gradeEssay_task2TooShort_rejectsBeforeAiAndPersistence() {
        User user = User.builder().userId(1L).build();
        WritingPrompt prompt = WritingPrompt.builder()
                .promptId(20L)
                .promptText("Discuss both views.")
                .essayType(EssayType.DISCUSSION)
                .taskType(WritingTaskType.TASK_2)
                .build();
        WritingGradeRequest request = new WritingGradeRequest(20L, "short task two essay");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptRepository.findById(20L)).thenReturn(Optional.of(prompt));
        when(writingGradingService.countWords(request.getEssayText())).thenReturn(249);

        WordCountTooLowException ex = assertThrows(
                WordCountTooLowException.class,
                () -> writingService.gradeEssay(1L, request));

        assertTrue(ex.getMessage().contains("Task 2"));
        verify(writingGradingService, never()).evaluateEssay(any(), any(), anyBoolean());
        verify(submissionRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
        verify(writingQueryService, never()).buildGradeResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an essay sent with an attempt past its deadline is refused before AI grading")
    void gradeEssay_lateAttempt_refusedBeforeAi() {
        WritingPrompt prompt = WritingPrompt.builder()
                .promptId(20L)
                .promptText("Discuss both views.")
                .essayType(EssayType.DISCUSSION)
                .taskType(WritingTaskType.TASK_2)
                .build();
        WritingGradeRequest request = new WritingGradeRequest(20L, "an essay", true, 9L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));
        when(promptRepository.findById(20L)).thenReturn(Optional.of(prompt));
        doThrow(new IllegalArgumentException("Time is up for this test, so it can no longer be submitted."))
                .when(examAttemptService).assertWithinDeadline(9L, 1L, SkillType.WRITING);

        assertThrows(IllegalArgumentException.class, () -> writingService.gradeEssay(1L, request));

        verify(writingGradingService, never()).evaluateEssay(any(), any(), anyBoolean());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("an essay written without an attempt (untimed) is not checked against a deadline")
    void gradeEssay_noAttempt_noDeadlineCheck() {
        WritingPrompt prompt = WritingPrompt.builder()
                .promptId(20L)
                .promptText("Discuss both views.")
                .essayType(EssayType.DISCUSSION)
                .taskType(WritingTaskType.TASK_2)
                .build();
        WritingGradeRequest request = new WritingGradeRequest(20L, "short essay");

        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));
        when(promptRepository.findById(20L)).thenReturn(Optional.of(prompt));
        when(writingGradingService.countWords(request.getEssayText())).thenReturn(2);

        // Stops at the word minimum, which comes after the deadline check would have.
        assertThrows(WordCountTooLowException.class, () -> writingService.gradeEssay(1L, request));

        verify(examAttemptService, never()).assertWithinDeadline(any(), any(), any());
    }
}
