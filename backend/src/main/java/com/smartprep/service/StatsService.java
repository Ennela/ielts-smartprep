package com.smartprep.service;

import com.smartprep.dto.response.AnalyticsOverviewResponse;
import com.smartprep.dto.response.ScoreTrendResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;

    // Simple in-memory cache: userId -> (timestamp, result)
    private final ConcurrentHashMap<Long, CacheEntry<AnalyticsOverviewResponse>> overviewCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    // ========== Overview ==========

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(Long userId) {
        // Check cache
        CacheEntry<AnalyticsOverviewResponse> cached = overviewCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Get skill averages
        List<Object[]> averages = scoreHistoryRepository.getSkillAverages(userId);

        Map<String, BigDecimal> avgScores = new HashMap<>();
        Map<String, Long> totalTests = new HashMap<>();
        for (Object[] row : averages) {
            String skill = ((SkillType) row[0]).name();
            BigDecimal avg = BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP);
            long count = ((Number) row[2]).longValue();
            avgScores.put(skill, avg);
            totalTests.put(skill, count);
        }

        // Build skill progress cards
        List<AnalyticsOverviewResponse.SkillProgress> skills = new ArrayList<>();
        skills.add(buildSkillProgress("READING", avgScores, totalTests, user.getTargetReadingScore()));
        skills.add(buildSkillProgress("WRITING", avgScores, totalTests, user.getTargetWritingScore()));
        skills.add(buildSkillProgress("LISTENING", avgScores, totalTests, user.getTargetListeningScore()));

        long totalTestCount = totalTests.values().stream().mapToLong(Long::longValue).sum();
        long metTargetCount = skills.stream().filter(s -> s.getProgressPercent() >= 100).count();

        AnalyticsOverviewResponse response = AnalyticsOverviewResponse.builder()
                .skills(skills)
                .totalTests(totalTestCount)
                .targetMetCount(metTargetCount)
                .build();

        // Update cache
        overviewCache.put(userId, new CacheEntry<>(response));
        return response;
    }

    // ========== Score Trend ==========

    @Transactional(readOnly = true)
    public ScoreTrendResponse getScoreTrend(Long userId, String skill, String period) {
        LocalDateTime since = LocalDateTime.now().minusMonths(6);
        String skillUpper = skill.toUpperCase();

        List<Object[]> rawData;
        if ("WEEKLY".equalsIgnoreCase(period)) {
            rawData = scoreHistoryRepository.getWeeklyTrend(userId, skillUpper, since);
        } else {
            rawData = scoreHistoryRepository.getMonthlyTrend(userId, skillUpper, since);
        }

        List<ScoreTrendResponse.DataPoint> dataPoints = rawData.stream()
                .map(row -> ScoreTrendResponse.DataPoint.builder()
                        .period(String.valueOf(row[0]))
                        .avgScore(BigDecimal.valueOf(((Number) row[1]).doubleValue())
                                .setScale(1, RoundingMode.HALF_UP))
                        .build())
                .collect(Collectors.toList());

        // Get target score for this skill
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BigDecimal target = getTargetForSkill(user, skillUpper);

        return ScoreTrendResponse.builder()
                .skill(skillUpper)
                .period(period.toUpperCase())
                .targetScore(target)
                .dataPoints(dataPoints)
                .build();
    }

    // ========== Paginated History ==========

    @Transactional(readOnly = true)
    public Map<String, Object> getHistory(Long userId, String skillFilter, int page, int size) {
        Page<ScoreHistory> historyPage;
        PageRequest pageRequest = PageRequest.of(page, size);

        if (skillFilter != null && !skillFilter.isBlank()) {
            SkillType type = SkillType.valueOf(skillFilter.toUpperCase());
            historyPage = scoreHistoryRepository.findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
                    userId, type, pageRequest);
        } else {
            historyPage = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(userId, pageRequest);
        }

        List<Map<String, Object>> items = historyPage.getContent().stream()
                .map(h -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("historyId", h.getHistoryId());
                    item.put("skillType", h.getSkillType().name());
                    item.put("score", h.getScore());
                    item.put("recordedAt", h.getRecordedAt());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", historyPage.getNumber());
        result.put("size", historyPage.getSize());
        result.put("totalItems", historyPage.getTotalElements());
        result.put("totalPages", historyPage.getTotalPages());
        return result;
    }

    // ========== Helpers ==========

    private AnalyticsOverviewResponse.SkillProgress buildSkillProgress(
            String skill, Map<String, BigDecimal> avgScores,
            Map<String, Long> totalTests, BigDecimal target) {

        BigDecimal currentAvg = avgScores.getOrDefault(skill, BigDecimal.ZERO);
        long count = totalTests.getOrDefault(skill, 0L);
        BigDecimal safeTarget = (target != null && target.compareTo(BigDecimal.ZERO) > 0)
                ? target : new BigDecimal("6.5");

        double progressPercent = Math.min(100.0,
                currentAvg.divide(safeTarget, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue());

        String status = currentAvg.compareTo(safeTarget) >= 0
                ? "TARGET_MET"
                : "IN_PROGRESS";

        BigDecimal gap = safeTarget.subtract(currentAvg).max(BigDecimal.ZERO);

        return AnalyticsOverviewResponse.SkillProgress.builder()
                .skill(skill)
                .currentAvg(currentAvg)
                .targetScore(safeTarget)
                .progressPercent(progressPercent)
                .totalTests(count)
                .status(status)
                .gap(gap)
                .build();
    }

    private BigDecimal getTargetForSkill(User user, String skill) {
        return switch (skill) {
            case "READING" -> user.getTargetReadingScore() != null ? user.getTargetReadingScore() : new BigDecimal("6.5");
            case "WRITING" -> user.getTargetWritingScore() != null ? user.getTargetWritingScore() : new BigDecimal("6.5");
            case "LISTENING" -> user.getTargetListeningScore() != null ? user.getTargetListeningScore() : new BigDecimal("6.5");
            default -> new BigDecimal("6.5");
        };
    }

    // Simple cache entry
    private static class CacheEntry<T> {
        final T value;
        final long timestamp;

        CacheEntry(T value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
