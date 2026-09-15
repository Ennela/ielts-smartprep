package com.smartprep.service;

import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The dashboard's Recent Activity table reads {@code /stats/history}. A row a full mock
 * test wrote -- or V48 backfilled -- has to name its submission so the table can link to
 * the mock test report rather than to an answer review that may be empty.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceHistoryTest {

    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private StatsService statsService;

    @Test
    @SuppressWarnings("unchecked")
    void historyItems_carryTheMockTestSubmissionIdOnlyForMockTestRows() {
        User user = User.builder().userId(1L).build();
        ScoreHistory practice = ScoreHistory.builder()
                .historyId(1L).user(user).skillType(SkillType.READING)
                .score(new BigDecimal("6.5")).difficulty("PASSAGE_2")
                .recordedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
        ScoreHistory fromMockTest = ScoreHistory.builder()
                .historyId(2L).user(user).skillType(SkillType.LISTENING)
                .score(new BigDecimal("7.0")).difficulty("MOCK_TEST")
                .recordedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .mockTestSubmission(MockTestSubmission.builder().submissionId(7L).build())
                .build();
        when(scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(fromMockTest, practice), PageRequest.of(0, 5), 2));

        Map<String, Object> result = statsService.getHistory(1L, null, 0, 5);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("historyId", 2L).containsEntry("mockTestSubmissionId", 7L);
        assertThat(items.get(1)).containsEntry("historyId", 1L).containsEntry("mockTestSubmissionId", null);
        // The page envelope is unchanged.
        assertThat(result).containsEntry("totalItems", 2L).containsEntry("totalPages", 1);
    }
}
