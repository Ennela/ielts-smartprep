package com.smartprep.service;

import com.smartprep.dto.request.AdminMockTestRequest;
import com.smartprep.dto.response.MockTestResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.MockTestDifficulty;
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminMockTestServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private WritingPromptRepository writingPromptRepository;
    @Mock private ReadingQuizRepository readingQuizRepository;
    @Mock private MockTestRepository mockTestRepository;
    @Mock private ListeningPartRepository listeningPartRepository;

    @InjectMocks private AdminService adminService;

    @ParameterizedTest
    @ValueSource(strings = {"EASY", "MEDIUM", "HARD", "easy", "medium", "hard"})
    @DisplayName("createMockTest successfully accepts standard semantic difficulties")
    void createMockTest_ValidDifficulty(String difficultyStr) {
        AdminMockTestRequest request = new AdminMockTestRequest();
        request.setTitle("Full Mock Test 1");
        request.setDescription("Academic practice test");
        request.setDifficulty(difficultyStr);
        request.setListeningPartIds(List.of(1L, 2L, 3L, 4L));
        request.setReadingQuizIds(List.of(10L, 11L, 12L));
        request.setWritingPromptIds(List.of(20L, 21L));

        when(listeningPartRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.of(ListeningPart.builder().partId(inv.getArgument(0)).build()));
        when(readingQuizRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.of(ReadingQuiz.builder().quizId(inv.getArgument(0)).build()));
        when(writingPromptRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.of(WritingPrompt.builder().promptId(inv.getArgument(0)).taskType(WritingTaskType.TASK_1).build()));

        when(mockTestRepository.save(any(MockTest.class))).thenAnswer(inv -> {
            MockTest mt = inv.getArgument(0);
            mt.setMockTestId(100L);
            return mt;
        });

        MockTestResponse response = adminService.createMockTest(request);

        assertNotNull(response);
        assertEquals(100L, response.getMockTestId());
        assertEquals("Full Mock Test 1", response.getTitle());
        assertEquals(MockTestDifficulty.valueOf(difficultyStr.toUpperCase()), response.getDifficulty());
        assertEquals(4, response.getListeningPartsCount());
        assertEquals(3, response.getReadingQuizzesCount());
        assertEquals(2, response.getWritingPromptsCount());

        ArgumentCaptor<MockTest> captor = ArgumentCaptor.forClass(MockTest.class);
        verify(mockTestRepository).save(captor.capture());
        assertEquals(MockTestDifficulty.valueOf(difficultyStr.toUpperCase()), captor.getValue().getDifficulty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSAGE_1", "PASSAGE_2", "PASSAGE_3", "INVALID", ""})
    @DisplayName("createMockTest throws IllegalArgumentException for non-MockTestDifficulty values")
    void createMockTest_InvalidDifficulty_ThrowsException(String invalidDifficulty) {
        AdminMockTestRequest request = new AdminMockTestRequest();
        request.setTitle("Invalid Mock");
        request.setDifficulty(invalidDifficulty);
        request.setListeningPartIds(List.of());
        request.setReadingQuizIds(List.of());
        request.setWritingPromptIds(List.of());

        assertThrows(IllegalArgumentException.class, () -> adminService.createMockTest(request));
        verify(mockTestRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMockTest updates difficulty to new MockTestDifficulty")
    void updateMockTest_Success() {
        MockTest existing = MockTest.builder()
                .mockTestId(11L)
                .title("Old Mock")
                .difficulty(MockTestDifficulty.EASY)
                .build();

        when(mockTestRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(mockTestRepository.save(any(MockTest.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminMockTestRequest request = new AdminMockTestRequest();
        request.setTitle("Updated Mock");
        request.setDescription("Updated desc");
        request.setDifficulty("HARD");
        request.setListeningPartIds(List.of());
        request.setReadingQuizIds(List.of());
        request.setWritingPromptIds(List.of());

        MockTestResponse response = adminService.updateMockTest(11L, request);

        assertNotNull(response);
        assertEquals(MockTestDifficulty.HARD, response.getDifficulty());
        assertEquals("Updated Mock", response.getTitle());
    }
}
