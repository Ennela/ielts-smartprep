package com.smartprep.service;

import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;

    public static class OverviewDto {
        public int totalTests;
        public Map<String, Double> averageScores;
        public Map<String, Double> targetScores;
        public Map<String, Long> testsBySkill;
        public String improvementTip;
    }

    public static class TrendPointDto {
        public String date;
        public double score;
    }

    public static class WeaknessDto {
        public String weakestType;
        public Double weakestAccuracy;
        public Map<String, Double> accuracies;
        public String recommendation;
    }

    @Transactional(readOnly = true)
    public OverviewDto getOverview(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ScoreHistory> history = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(userId);

        OverviewDto dto = new OverviewDto();
        dto.totalTests = history.size();

        // Skill averages & test count
        dto.averageScores = new HashMap<>();
        dto.testsBySkill = new HashMap<>();
        for (SkillType skill : SkillType.values()) {
            dto.averageScores.put(skill.name(), 0.0);
            dto.testsBySkill.put(skill.name(), 0L);
        }

        Map<SkillType, List<ScoreHistory>> grouped = history.stream()
                .collect(Collectors.groupingBy(ScoreHistory::getSkillType));

        for (Map.Entry<SkillType, List<ScoreHistory>> entry : grouped.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(h -> h.getScore().doubleValue())
                    .average()
                    .orElse(0.0);
            // round to 2 decimal places
            avg = Math.round(avg * 100.0) / 100.0;
            dto.averageScores.put(entry.getKey().name(), avg);
            dto.testsBySkill.put(entry.getKey().name(), (long) entry.getValue().size());
        }

        // Targets
        dto.targetScores = new HashMap<>();
        dto.targetScores.put("READING", user.getTargetReadingScore() != null ? user.getTargetReadingScore().doubleValue() : 6.0);
        dto.targetScores.put("LISTENING", user.getTargetListeningScore() != null ? user.getTargetListeningScore().doubleValue() : 6.0);
        dto.targetScores.put("WRITING", user.getTargetWritingScore() != null ? user.getTargetWritingScore().doubleValue() : 6.0);

        // Simple improvement tip
        dto.improvementTip = generateOverallTip(dto.averageScores, dto.targetScores, dto.totalTests);

        return dto;
    }

    @Transactional(readOnly = true)
    public List<TrendPointDto> getScoreTrend(Long userId, SkillType skillType) {
        // Query score history in chronological order
        List<ScoreHistory> history = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(h -> h.getSkillType() == skillType)
                .sorted(Comparator.comparing(ScoreHistory::getRecordedAt))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return history.stream().map(h -> {
            TrendPointDto point = new TrendPointDto();
            point.date = h.getRecordedAt().format(formatter);
            point.score = h.getScore().doubleValue();
            return point;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WeaknessDto getWeakness(Long userId, SkillType skillType) {
        List<ScoreHistory> history = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(h -> skillType == null || h.getSkillType() == skillType)
                .collect(Collectors.toList());

        // Aggregate UserAnswers
        List<UserAnswer> answers = history.stream()
                .flatMap(h -> h.getUserAnswers().stream())
                .collect(Collectors.toList());

        WeaknessDto dto = new WeaknessDto();
        dto.accuracies = new HashMap<>();

        if (answers.isEmpty()) {
            dto.weakestType = "None";
            dto.weakestAccuracy = 100.0;
            dto.recommendation = "Hãy hoàn thành thêm bài kiểm tra để chúng tôi phân tích điểm yếu của bạn.";
            return dto;
        }

        Map<String, List<UserAnswer>> grouped = answers.stream()
                .filter(a -> a.getQuestionType() != null && !a.getQuestionType().isBlank())
                .collect(Collectors.groupingBy(UserAnswer::getQuestionType));

        String weakest = null;
        double lowestAcc = 100.0;

        for (Map.Entry<String, List<UserAnswer>> entry : grouped.entrySet()) {
            long correct = entry.getValue().stream().filter(UserAnswer::getIsCorrect).count();
            double acc = (double) correct / entry.getValue().size() * 100.0;
            acc = Math.round(acc * 10.0) / 10.0;
            dto.accuracies.put(entry.getKey(), acc);

            if (acc < lowestAcc) {
                lowestAcc = acc;
                weakest = entry.getKey();
            }
        }

        if (weakest != null && lowestAcc < 85.0) {
            dto.weakestType = weakest;
            dto.weakestAccuracy = lowestAcc;
            dto.recommendation = String.format("Dạng bài %s của bạn có tỉ lệ chính xác thấp nhất (%.1f%%). Hãy tập trung luyện tập dạng bài này để cải thiện điểm số.", weakest, lowestAcc);
        } else {
            dto.weakestType = weakest != null ? weakest : "None";
            dto.weakestAccuracy = weakest != null ? lowestAcc : 100.0;
            dto.recommendation = "Tất cả các dạng bài của bạn đều đạt tỉ lệ đúng cao. Hãy duy trì phong độ và thử thách với độ khó cao hơn!";
        }

        return dto;
    }

    private String generateOverallTip(Map<String, Double> averages, Map<String, Double> targets, int totalTests) {
        if (totalTests == 0) {
            return "Chào mừng bạn đến với SmartPrep! Hãy bắt đầu làm bài luyện tập Reading hoặc Listening để hệ thống phân tích tiến độ học tập của bạn.";
        }

        List<String> areasOfImprovement = new ArrayList<>();
        for (String skill : averages.keySet()) {
            Double targetVal = targets.get(skill);
            if (targetVal == null) {
                continue;
            }
            double avg = averages.get(skill);
            double target = targetVal;
            if (avg < target) {
                areasOfImprovement.add(skill.toLowerCase());
            }
        }

        if (areasOfImprovement.isEmpty()) {
            return "Tuyệt vời! Điểm số trung bình hiện tại của bạn đã đạt hoặc vượt mục tiêu đề ra cho tất cả các kỹ năng. Hãy tiếp tục luyện tập để duy trì phong độ ổn định.";
        }

        String skillsStr = String.join(", ", areasOfImprovement);
        return String.format("Bạn cần tập trung cải thiện thêm ở kỹ năng: %s. Điểm trung bình của các phần này hiện tại đang dưới mức điểm mục tiêu của bạn.", skillsStr);
    }
}
