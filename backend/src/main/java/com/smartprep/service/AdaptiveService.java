package com.smartprep.service;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptiveService {

    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;

    public static class AdaptiveConfig {
        public double estimatedBand;
        public String nextDifficulty; // "PASSAGE_1", "PASSAGE_2", "PASSAGE_3" for Reading; "1", "2", "3", "4" for Listening
        public String focusQuestionType; // name of QuestionType enum or null
        public String reason;
    }

    public AdaptiveConfig suggestNextConfig(Long userId, SkillType skillType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ScoreHistory> history = scoreHistoryRepository.findByUserUserIdAndSkillTypeOrderByRecordedAtDesc(
                userId, skillType, PageRequest.of(0, 5)
        ).getContent();

        AdaptiveConfig config = new AdaptiveConfig();

        // 1. Calculate Estimated Band
        if (history.isEmpty()) {
            BigDecimal targetScore = null;
            if (skillType == SkillType.READING) {
                targetScore = user.getTargetReadingScore();
            } else if (skillType == SkillType.LISTENING) {
                targetScore = user.getTargetListeningScore();
            }
            config.estimatedBand = (targetScore != null) ? targetScore.doubleValue() : 6.0;
        } else {
            double sum = history.stream()
                    .map(ScoreHistory::getScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .sum();
            config.estimatedBand = BigDecimal.valueOf(sum / history.size())
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // 2. Identify Focus Question Type (the one with the lowest accuracy in recent attempts)
        List<UserAnswer> allAnswers = history.stream()
                .flatMap(h -> h.getUserAnswers().stream())
                .collect(Collectors.toList());

        if (allAnswers.isEmpty()) {
            config.focusQuestionType = null;
        } else {
            Map<String, List<UserAnswer>> grouped = allAnswers.stream()
                    .filter(a -> a.getQuestionType() != null)
                    .collect(Collectors.groupingBy(UserAnswer::getQuestionType));

            String worstType = null;
            double lowestAccuracy = 1.0;

            for (Map.Entry<String, List<UserAnswer>> entry : grouped.entrySet()) {
                long correct = entry.getValue().stream().filter(UserAnswer::getIsCorrect).count();
                double acc = (double) correct / entry.getValue().size();
                if (acc < lowestAccuracy) {
                    lowestAccuracy = acc;
                    worstType = entry.getKey();
                } else if (acc == lowestAccuracy && worstType != null) {
                    // Tie-breaker: prefer type with more total questions to have a reliable signal
                    if (entry.getValue().size() > grouped.get(worstType).size()) {
                        worstType = entry.getKey();
                    }
                }
            }
            
            // Only suggest focus if accuracy is below 85%
            if (lowestAccuracy < 0.85) {
                config.focusQuestionType = worstType;
            } else {
                config.focusQuestionType = null;
            }
        }

        // 3. Determine next target difficulty level based on promotion/demotion logic from the most recent test
        if (history.isEmpty()) {
            // Default mappings based on estimated/target band
            if (skillType == SkillType.READING) {
                if (config.estimatedBand < 5.5) {
                    config.nextDifficulty = Difficulty.PASSAGE_1.name();
                } else if (config.estimatedBand < 7.0) {
                    config.nextDifficulty = Difficulty.PASSAGE_2.name();
                } else {
                    config.nextDifficulty = Difficulty.PASSAGE_3.name();
                }
            } else {
                if (config.estimatedBand < 5.5) {
                    config.nextDifficulty = "1";
                } else if (config.estimatedBand < 6.5) {
                    config.nextDifficulty = "2";
                } else if (config.estimatedBand < 7.5) {
                    config.nextDifficulty = "3";
                } else {
                    config.nextDifficulty = "4";
                }
            }
            config.reason = "Đây là bài luyện tập đầu tiên của bạn. Độ khó đề xuất dựa trên điểm mục tiêu hoặc năng lực mặc định.";
        } else {
            ScoreHistory latest = history.get(0);
            List<UserAnswer> latestAnswers = latest.getUserAnswers();
            long correct = latestAnswers.stream().filter(UserAnswer::getIsCorrect).count();
            int total = latestAnswers.size();
            double lastAccuracy = total > 0 ? (double) correct / total : 0;

            String currentDiff = latest.getDifficulty();
            if (currentDiff == null || "FULL_TEST".equals(currentDiff)) {
                // Fallback to average/estimated mapping if previous difficulty is unknown or was a Full Test
                if (skillType == SkillType.READING) {
                    currentDiff = Difficulty.PASSAGE_1.name();
                } else {
                    currentDiff = "1";
                }
            }

            if (skillType == SkillType.READING) {
                Difficulty currentEnum = Difficulty.PASSAGE_1;
                try {
                    currentEnum = Difficulty.valueOf(currentDiff);
                } catch (Exception ignored) {}

                Difficulty nextEnum = currentEnum;
                if (lastAccuracy > 0.85) {
                    // Promote
                    if (currentEnum == Difficulty.PASSAGE_1) nextEnum = Difficulty.PASSAGE_2;
                    else if (currentEnum == Difficulty.PASSAGE_2) nextEnum = Difficulty.PASSAGE_3;
                } else if (lastAccuracy < 0.50) {
                    // Demote
                    if (currentEnum == Difficulty.PASSAGE_3) nextEnum = Difficulty.PASSAGE_2;
                    else if (currentEnum == Difficulty.PASSAGE_2) nextEnum = Difficulty.PASSAGE_1;
                }

                config.nextDifficulty = nextEnum.name();
                config.reason = buildVietnameseReason(skillType, currentEnum.name(), nextEnum.name(), lastAccuracy, config.focusQuestionType);
            } else {
                // Listening: diff is string part number "1" - "4"
                int currentPart = 1;
                try {
                    if (currentDiff.startsWith("PART_")) {
                        currentPart = Integer.parseInt(currentDiff.substring(5));
                    } else {
                        currentPart = Integer.parseInt(currentDiff);
                    }
                } catch (Exception ignored) {}

                int nextPart = currentPart;
                if (lastAccuracy > 0.85) {
                    if (currentPart < 4) nextPart = currentPart + 1;
                } else if (lastAccuracy < 0.50) {
                    if (currentPart > 1) nextPart = currentPart - 1;
                }

                config.nextDifficulty = String.valueOf(nextPart);
                config.reason = buildVietnameseReason(skillType, "Part " + currentPart, "Part " + nextPart, lastAccuracy, config.focusQuestionType);
            }
        }

        return config;
    }

    private String buildVietnameseReason(SkillType skill, String prev, String next, double lastAccuracy, String focusType) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Kết quả bài luyện tập %s gần nhất của bạn đạt tỷ lệ chính xác %.0f%%. ",
                (skill == SkillType.READING ? "Reading" : "Listening"), lastAccuracy * 100));

        if (lastAccuracy > 0.85) {
            if (prev.equals(next)) {
                sb.append("Tỷ lệ chính xác rất cao (>85%), bạn đang ở mức độ khó tối đa.");
            } else {
                sb.append(String.format("Tỷ lệ chính xác rất cao (>85%%), hệ thống tự động tăng độ khó từ %s lên %s.", prev, next));
            }
        } else if (lastAccuracy < 0.50) {
            if (prev.equals(next)) {
                sb.append("Tỷ lệ chính xác thấp (<50%), bạn đang ở mức độ khó tối thiểu để ôn tập lại.");
            } else {
                sb.append(String.format("Tỷ lệ chính xác thấp (<50%%), hệ thống tự động giảm độ khó từ %s xuống %s để củng cố nền tảng.", prev, next));
            }
        } else {
            sb.append(String.format("Tỷ lệ chính xác ổn định, tiếp tục luyện tập ở độ khó %s.", next));
        }

        if (focusType != null) {
            sb.append(String.format(" Bạn cần tập trung cải thiện dạng bài %s do tỷ lệ chính xác còn thấp trong các bài thi gần đây.", focusType));
        }

        return sb.toString();
    }
}
