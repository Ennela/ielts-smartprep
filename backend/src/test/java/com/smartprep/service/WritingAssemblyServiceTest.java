package com.smartprep.service;

import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.exception.WordCountTooLowException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingFullSubmissionRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.repository.WritingSubmissionRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingAssemblyServiceTest {

    @Mock private WritingPromptRepository promptRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private WritingService writingService;
    @Mock private WritingQueryService writingQueryService;
    @Mock private WritingFullSubmissionRepository writingFullSubmissionRepository;
    @Mock private WritingSubmissionRepository writingSubmissionRepository;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks
    private WritingAssemblyService writingAssemblyService;

    @Test
    @DisplayName("Full Writing rejects Task 1 below 150 words before grading either task")
    void submitFullWriting_task1TooShort_rejectsBeforeGrading() {
        WritingSubmitFullRequest request = new WritingSubmitFullRequest();
        request.setTask1PromptId(10L);
        request.setTask1EssayText(words(149));
        request.setTask2PromptId(20L);
        request.setTask2EssayText(words(250));

        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));

        WordCountTooLowException ex = assertThrows(
                WordCountTooLowException.class,
                () -> writingAssemblyService.submitFullWriting(1L, request));

        assertTrue(ex.getMessage().contains("Task 1"));
        verify(writingService, never()).evaluateAndSaveSubmission(any(), anyLong(), any(), anyInt());
        verify(writingFullSubmissionRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Full Writing rejects Task 2 below 250 words before grading either task")
    void submitFullWriting_task2TooShort_rejectsBeforeGrading() {
        WritingSubmitFullRequest request = new WritingSubmitFullRequest();
        request.setTask1PromptId(10L);
        request.setTask1EssayText(words(150));
        request.setTask2PromptId(20L);
        request.setTask2EssayText(words(249));

        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));

        WordCountTooLowException ex = assertThrows(
                WordCountTooLowException.class,
                () -> writingAssemblyService.submitFullWriting(1L, request));

        assertTrue(ex.getMessage().contains("Task 2"));
        verify(writingService, never()).evaluateAndSaveSubmission(any(), anyLong(), any(), anyInt());
        verify(writingFullSubmissionRepository, never()).save(any());
        verify(scoreHistoryRepository, never()).save(any());
    }

    private String words(int count) {
        return "word ".repeat(count).trim();
    }
}
