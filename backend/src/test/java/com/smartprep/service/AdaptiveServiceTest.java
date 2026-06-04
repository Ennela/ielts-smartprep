package com.smartprep.service;

import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdaptiveServiceTest {

    @Mock
    private ScoreHistoryRepository scoreHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdaptiveService adaptiveService; // Wait, type is AdaptiveService, field name can be lowercase

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("student@test.com")
                .targetReadingScore(new BigDecimal("7.5"))
                .targetListeningScore(new BigDecimal("8.0"))
                .build();
    }

    @Test
    public void testSuggestNextConfig_EmptyHistory_ReturnsDefaultConfigFromTarget() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(scoreHistoryRepository.findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
                eq(1L), eq(SkillType.READING), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));

        AdaptiveService.AdaptiveConfig config = adaptiveService.suggestNextConfig(1L, SkillType.READING);

        assertNotNull(config);
        assertEquals(7.5, config.estimatedBand);
        assertEquals(Difficulty.PASSAGE_3.name(), config.nextDifficulty);
        assertNull(config.focusQuestionType);
        assertTrue(config.reason.contains("Độ khó đề xuất dựa trên điểm mục tiêu"));
    }

    @Test
    public void testSuggestNextConfig_PromotionLogic() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        List<UserAnswer> userAnswers = new ArrayList<>();
        // 9 out of 10 correct -> 90% accuracy (>85%)
        for (int i = 0; i < 9; i++) {
            userAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(true).build());
        }
        userAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(false).build());

        ScoreHistory latest = ScoreHistory.builder()
                .score(new BigDecimal("8.0"))
                .difficulty("PASSAGE_2")
                .userAnswers(userAnswers)
                .build();

        when(scoreHistoryRepository.findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
                eq(1L), eq(SkillType.READING), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(latest)));

        AdaptiveService.AdaptiveConfig config = adaptiveService.suggestNextConfig(1L, SkillType.READING);

        assertNotNull(config);
        assertEquals(8.0, config.estimatedBand);
        // Promoted from PASSAGE_2 to PASSAGE_3
        assertEquals(Difficulty.PASSAGE_3.name(), config.nextDifficulty);
        assertNull(config.focusQuestionType); // accuracy is high (90% >= 85%)
        assertTrue(config.reason.contains("tăng độ khó từ PASSAGE_2 lên PASSAGE_3"));
    }

    @Test
    public void testSuggestNextConfig_DemotionLogic_WithFocusQuestionType() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        List<UserAnswer> userAnswers = new ArrayList<>();
        // 3 out of 10 correct -> 30% accuracy (<50%)
        // 5 MCQ (1 correct) and 5 TFNG (2 correct)
        userAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(true).build());
        for (int i = 0; i < 4; i++) {
            userAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(false).build());
        }
        for (int i = 0; i < 2; i++) {
            userAnswers.add(UserAnswer.builder().questionType("TFNG").isCorrect(true).build());
        }
        for (int i = 0; i < 3; i++) {
            userAnswers.add(UserAnswer.builder().questionType("TFNG").isCorrect(false).build());
        }

        ScoreHistory latest = ScoreHistory.builder()
                .score(new BigDecimal("4.0"))
                .difficulty("PASSAGE_2")
                .userAnswers(userAnswers)
                .build();

        when(scoreHistoryRepository.findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
                eq(1L), eq(SkillType.READING), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(latest)));

        AdaptiveService.AdaptiveConfig config = adaptiveService.suggestNextConfig(1L, SkillType.READING);

        assertNotNull(config);
        assertEquals(4.0, config.estimatedBand);
        // Demoted from PASSAGE_2 to PASSAGE_1
        assertEquals(Difficulty.PASSAGE_1.name(), config.nextDifficulty);
        // MCQ accuracy (20%) is lower than TFNG accuracy (40%)
        assertEquals("MCQ", config.focusQuestionType);
        assertTrue(config.reason.contains("giảm độ khó từ PASSAGE_2 xuống PASSAGE_1"));
        assertTrue(config.reason.contains("tập trung cải thiện dạng bài MCQ"));
    }
}
