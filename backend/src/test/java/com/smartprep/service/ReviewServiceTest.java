package com.smartprep.service;

import com.smartprep.dto.response.HistoryDetailResponse;
import com.smartprep.dto.response.UserAnswerResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserAnswerRepository;
import com.smartprep.service.ai.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ScoreHistoryRepository scoreHistoryRepository;

    @Mock
    private UserAnswerRepository userAnswerRepository;

    @Mock
    private GeminiClient geminiClient;

    @InjectMocks
    private ReviewService reviewService;

    private User user;
    private ScoreHistory history;
    private List<UserAnswer> answers;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).build();

        history = ScoreHistory.builder()
                .historyId(10L)
                .user(user)
                .skillType(SkillType.READING)
                .score(new BigDecimal("6.5"))
                .recordedAt(LocalDateTime.now())
                .build();

        answers = List.of(
                UserAnswer.builder()
                        .answerId(100L)
                        .scoreHistory(history)
                        .questionNo(1)
                        .questionText("What is the main idea?")
                        .questionType("MCQ")
                        .userAnswer("B")
                        .correctAnswer("B")
                        .isCorrect(true)
                        .build(),
                UserAnswer.builder()
                        .answerId(101L)
                        .scoreHistory(history)
                        .questionNo(2)
                        .questionText("The author mentions X because...")
                        .questionType("MCQ")
                        .userAnswer("A")
                        .correctAnswer("C")
                        .isCorrect(false)
                        .build()
        );
    }

    @Test
    void getHistoryDetail_shouldReturnDetailWithAnswers() {
        when(scoreHistoryRepository.findById(10L)).thenReturn(Optional.of(history));
        when(userAnswerRepository.findByScoreHistoryHistoryIdOrderByQuestionNoAsc(10L))
                .thenReturn(answers);

        HistoryDetailResponse result = reviewService.getHistoryDetail(10L, 1L);

        assertThat(result.getHistoryId()).isEqualTo(10L);
        assertThat(result.getSkillType()).isEqualTo("READING");
        assertThat(result.getScore()).isEqualByComparingTo("6.5");
        assertThat(result.getTotalQuestions()).isEqualTo(2);
        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getAnswers()).hasSize(2);
        assertThat(result.getAnswers().get(0).getIsCorrect()).isTrue();
        assertThat(result.getAnswers().get(1).getIsCorrect()).isFalse();
    }

    @Test
    void getHistoryDetail_shouldThrowWhenHistoryNotFound() {
        when(scoreHistoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getHistoryDetail(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHistoryDetail_shouldThrowWhenUserDoesNotOwnHistory() {
        when(scoreHistoryRepository.findById(10L)).thenReturn(Optional.of(history));

        // User 2 trying to access User 1's history
        assertThatThrownBy(() -> reviewService.getHistoryDetail(10L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void explainAnswer_shouldReturnCachedExplanation() {
        UserAnswer answerWithExplanation = UserAnswer.builder()
                .answerId(101L)
                .scoreHistory(history)
                .questionNo(2)
                .questionText("Question text")
                .questionType("MCQ")
                .userAnswer("A")
                .correctAnswer("C")
                .isCorrect(false)
                .explanation("The correct answer is C because...")
                .build();

        when(scoreHistoryRepository.findById(10L)).thenReturn(Optional.of(history));
        when(userAnswerRepository.findById(101L)).thenReturn(Optional.of(answerWithExplanation));

        UserAnswerResponse result = reviewService.explainAnswer(10L, 101L, 1L);

        assertThat(result.getExplanation()).isEqualTo("The correct answer is C because...");
        // Should NOT call Gemini if explanation already cached
        verify(geminiClient, never()).generate(anyString(), anyString());
    }

    @Test
    void explainAnswer_shouldCallGeminiAndCacheResult() {
        UserAnswer answerWithoutExplanation = UserAnswer.builder()
                .answerId(101L)
                .scoreHistory(history)
                .questionNo(2)
                .questionText("Question text")
                .questionType("MCQ")
                .userAnswer("A")
                .correctAnswer("C")
                .isCorrect(false)
                .build();

        when(scoreHistoryRepository.findById(10L)).thenReturn(Optional.of(history));
        when(userAnswerRepository.findById(101L)).thenReturn(Optional.of(answerWithoutExplanation));
        when(geminiClient.generate(anyString(), anyString()))
                .thenReturn("AI generated explanation");

        UserAnswerResponse result = reviewService.explainAnswer(10L, 101L, 1L);

        assertThat(result.getExplanation()).isEqualTo("AI generated explanation");
        verify(geminiClient).generate(anyString(), anyString());
        verify(userAnswerRepository).save(answerWithoutExplanation);
    }

    @Test
    void explainAnswer_shouldThrowWhenAnswerDoesNotBelongToHistory() {
        ScoreHistory otherHistory = ScoreHistory.builder()
                .historyId(20L)
                .user(user)
                .skillType(SkillType.READING)
                .score(new BigDecimal("5.0"))
                .recordedAt(LocalDateTime.now())
                .build();

        UserAnswer answerFromOtherHistory = UserAnswer.builder()
                .answerId(200L)
                .scoreHistory(otherHistory)
                .questionNo(1)
                .questionText("Other question")
                .questionType("MCQ")
                .userAnswer("A")
                .correctAnswer("B")
                .isCorrect(false)
                .build();

        when(scoreHistoryRepository.findById(10L)).thenReturn(Optional.of(history));
        when(userAnswerRepository.findById(200L)).thenReturn(Optional.of(answerFromOtherHistory));

        assertThatThrownBy(() -> reviewService.explainAnswer(10L, 200L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong");
    }
}
