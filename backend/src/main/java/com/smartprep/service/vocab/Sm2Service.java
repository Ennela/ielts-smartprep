package com.smartprep.service.vocab;

import com.smartprep.model.entity.Vocabulary;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class Sm2Service {

    public void updateReview(Vocabulary vocab, String grade) {
        updateReview(vocab, grade, LocalDateTime.now());
    }

    public void updateReview(Vocabulary vocab, String grade, LocalDateTime now) {
        int q = mapGradeToQuality(grade);

        // Update Repetitions & Interval
        if (q < 3) {
            vocab.setRepetitions(0);
            vocab.setIntervalDays(1);
        } else {
            int repetitions = vocab.getRepetitions();
            if (repetitions == 0) {
                vocab.setIntervalDays(1);
            } else if (repetitions == 1) {
                vocab.setIntervalDays(6);
            } else {
                double newInterval = Math.round(vocab.getIntervalDays() * vocab.getEaseFactor());
                vocab.setIntervalDays((int) newInterval);
            }
            vocab.setRepetitions(vocab.getRepetitions() + 1);
        }

        // Update Ease Factor (EF)
        double ef = vocab.getEaseFactor() + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        if (ef < 1.3) {
            ef = 1.3;
        }
        vocab.setEaseFactor(ef);

        // Set Next Due Date
        vocab.setDueDate(now.plusDays(vocab.getIntervalDays()));
    }

    private int mapGradeToQuality(String grade) {
        if (grade == null) {
            return 3; // Default to Hard if null
        }
        return switch (grade.toUpperCase()) {
            case "AGAIN" -> 1;
            case "HARD" -> 3;
            case "GOOD" -> 4;
            case "EASY" -> 5;
            default -> 3;
        };
    }
}
