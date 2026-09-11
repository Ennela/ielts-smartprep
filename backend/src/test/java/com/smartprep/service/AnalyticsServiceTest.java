package com.smartprep.service;

import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserAnswerRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private ScoreHistoryRepository scoreHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAnswerRepository userAnswerRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = User.builder()
                .userId(1L)
                .email("student@test.com")
                .targetReadingScore(new BigDecimal("7.5"))
                .targetListeningScore(new BigDecimal("8.0"))
                .targetWritingScore(new BigDecimal("7.0"))
                .build();
    }

    @Test
    public void testGetOverview_CalculatesAveragesAndTargets() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        ScoreHistory readingAttempt = ScoreHistory.builder()
                .score(new BigDecimal("7.0"))
                .skillType(SkillType.READING)
                .build();
        ScoreHistory readingAttempt2 = ScoreHistory.builder()
                .score(new BigDecimal("8.0"))
                .skillType(SkillType.READING)
                .build();
        ScoreHistory listeningAttempt = ScoreHistory.builder()
                .score(new BigDecimal("7.5"))
                .skillType(SkillType.LISTENING)
                .build();

        List<ScoreHistory> history = List.of(readingAttempt, readingAttempt2, listeningAttempt);
        when(scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(1L)).thenReturn(history);

        AnalyticsService.OverviewDto overview = analyticsService.getOverview(1L);

        assertNotNull(overview);
        assertEquals(3, overview.totalTests);
        assertEquals(7.5, overview.averageScores.get("READING"));
        assertEquals(7.5, overview.averageScores.get("LISTENING"));
        assertEquals(0.0, overview.averageScores.get("WRITING"));

        assertEquals(7.5, overview.targetScores.get("READING"));
        assertEquals(8.0, overview.targetScores.get("LISTENING"));
        assertEquals(7.0, overview.targetScores.get("WRITING"));

        assertEquals(2L, overview.testsBySkill.get("READING"));
        assertEquals(1L, overview.testsBySkill.get("LISTENING"));
        assertEquals(0L, overview.testsBySkill.get("WRITING"));
    }

    @Test
    public void testGetScoreTrend_ReturnsChronologicalSortedData() {
        LocalDateTime now = LocalDateTime.now();
        ScoreHistory attempt1 = ScoreHistory.builder()
                .score(new BigDecimal("6.0"))
                .skillType(SkillType.READING)
                .recordedAt(now.minusDays(2))
                .build();
        ScoreHistory attempt2 = ScoreHistory.builder()
                .score(new BigDecimal("7.0"))
                .skillType(SkillType.READING)
                .recordedAt(now.minusDays(1))
                .build();

        List<ScoreHistory> history = List.of(attempt2, attempt1); // Descending order as returned by repo
        when(scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(1L)).thenReturn(history);

        List<AnalyticsService.TrendPointDto> trend = analyticsService.getScoreTrend(1L, SkillType.READING);

        assertNotNull(trend);
        assertEquals(2, trend.size());
        assertEquals(6.0, trend.get(0).score); // earliest first
        assertEquals(7.0, trend.get(1).score);
    }

    @Test
    public void testGetWeakness_SelectsWeakestQuestionType() {
        // One aggregate row per question type: [type, correct, total].
        // MCQ: 1 of 3 (33.3%), TFNG: 2 of 3 (66.7%).
        when(userAnswerRepository.accuracyByQuestionType(1L, SkillType.READING)).thenReturn(List.<Object[]>of(
                new Object[]{"MCQ", 1L, 3L},
                new Object[]{"TFNG", 2L, 3L}));

        AnalyticsService.WeaknessDto weakness = analyticsService.getWeakness(1L, SkillType.READING);

        assertNotNull(weakness);
        assertEquals("MCQ", weakness.weakestType);
        assertEquals(33.3, weakness.weakestAccuracy);
        assertEquals(33.3, weakness.accuracies.get("MCQ"));
        assertEquals(66.7, weakness.accuracies.get("TFNG"));
        assertTrue(weakness.recommendation.contains("MCQ"));
        // The whole thing is one query; the per-sitting answer lists are never loaded.
        verify(scoreHistoryRepository, never()).findByUserUserIdOrderByRecordedAtDesc(anyLong());
    }

    @Test
    public void testGetWeakness_NoSkillFilterUsesAllSkills() {
        when(userAnswerRepository.accuracyByQuestionType(1L)).thenReturn(List.<Object[]>of(
                new Object[]{"FILL_BLANK", 9L, 10L}));

        AnalyticsService.WeaknessDto weakness = analyticsService.getWeakness(1L, null);

        assertEquals(90.0, weakness.accuracies.get("FILL_BLANK"));
        // 90% is above the 85% bar, so it is reported but not called a weakness.
        assertEquals("FILL_BLANK", weakness.weakestType);
        assertFalse(weakness.recommendation.contains("thấp nhất"));
        verify(userAnswerRepository, never()).accuracyByQuestionType(anyLong(), any());
    }

    @Test
    public void testGetWeakness_NoAnswersYet() {
        when(userAnswerRepository.accuracyByQuestionType(1L)).thenReturn(List.<Object[]>of());

        AnalyticsService.WeaknessDto weakness = analyticsService.getWeakness(1L, null);

        assertEquals("None", weakness.weakestType);
        assertEquals(100.0, weakness.weakestAccuracy);
        assertTrue(weakness.accuracies.isEmpty());
    }

    @Test
    public void testGetWeakness_TieIsBrokenAlphabetically() {
        // Both at 50%; the result used to depend on HashMap iteration order.
        when(userAnswerRepository.accuracyByQuestionType(1L, SkillType.LISTENING)).thenReturn(List.<Object[]>of(
                new Object[]{"SENTENCE_COMPLETION", 1L, 2L},
                new Object[]{"MCQ", 2L, 4L}));

        AnalyticsService.WeaknessDto weakness = analyticsService.getWeakness(1L, SkillType.LISTENING);

        assertEquals("MCQ", weakness.weakestType);
        assertEquals(50.0, weakness.weakestAccuracy);
    }
}
