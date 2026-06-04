package com.smartprep.service;

import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private ScoreHistoryRepository scoreHistoryRepository;

    @Mock
    private UserRepository userRepository;

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
        List<UserAnswer> readingAnswers = new ArrayList<>();
        // MCQ: 1 correct, 2 incorrect (33.3% accuracy)
        readingAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(true).build());
        readingAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(false).build());
        readingAnswers.add(UserAnswer.builder().questionType("MCQ").isCorrect(false).build());

        // TFNG: 2 correct, 1 incorrect (66.7% accuracy)
        readingAnswers.add(UserAnswer.builder().questionType("TFNG").isCorrect(true).build());
        readingAnswers.add(UserAnswer.builder().questionType("TFNG").isCorrect(true).build());
        readingAnswers.add(UserAnswer.builder().questionType("TFNG").isCorrect(false).build());

        ScoreHistory readingAttempt = ScoreHistory.builder()
                .score(new BigDecimal("6.0"))
                .skillType(SkillType.READING)
                .userAnswers(readingAnswers)
                .build();

        when(scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(1L)).thenReturn(List.of(readingAttempt));

        AnalyticsService.WeaknessDto weakness = analyticsService.getWeakness(1L, SkillType.READING);

        assertNotNull(weakness);
        assertEquals("MCQ", weakness.weakestType);
        assertEquals(33.3, weakness.weakestAccuracy);
        assertEquals(33.3, weakness.accuracies.get("MCQ"));
        assertEquals(66.7, weakness.accuracies.get("TFNG"));
        assertTrue(weakness.recommendation.contains("MCQ"));
    }
}
