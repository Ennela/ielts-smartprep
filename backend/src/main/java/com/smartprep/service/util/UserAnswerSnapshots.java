package com.smartprep.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.UserAnswer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the {@link UserAnswer} rows that a score-history entry keeps for later review.
 *
 * <p>Each row is a snapshot: the question text, the options as they were shown, the key
 * and the candidate's answer are copied at grading time, so the review page still makes
 * sense if the question is edited or removed afterwards.
 *
 * <p>Shared by Reading practice, Listening practice and the full mock test, which grade
 * the same question types and previously each carried their own copy of this.
 */
@Slf4j
public final class UserAnswerSnapshots {

    private UserAnswerSnapshots() {}

    public static UserAnswer forReading(ScoreHistory history, int questionNo, ReadingQuestion question,
                                        String userAnswer, boolean correct, ObjectMapper objectMapper) {
        String optionsSnapshot = null;
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            optionsSnapshot = optionsJson(question.getOptions(), question.getQuestionId(), objectMapper);
        } else if (question.getOptionsJson() != null) {
            optionsSnapshot = question.getOptionsJson();
        }

        return UserAnswer.builder()
                .scoreHistory(history).questionNo(questionNo)
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType().name())
                .userAnswer(userAnswer).correctAnswer(question.getCorrectAnswer())
                .isCorrect(correct).explanation(question.getExplanation())
                .optionsJson(optionsSnapshot)
                .evidenceText(question.getEvidenceText())
                .evidenceOffset(question.getEvidenceOffset())
                .evidenceLength(question.getEvidenceLength())
                .build();
    }

    public static UserAnswer forListening(ScoreHistory history, int questionNo, ListeningQuestion question,
                                          String userAnswer, boolean correct, ObjectMapper objectMapper) {
        String optionsSnapshot = null;
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            optionsSnapshot = optionsJson(question.getOptions(), question.getQuestionId(), objectMapper);
        }

        return UserAnswer.builder()
                .scoreHistory(history).questionNo(questionNo).questionText(question.getQuestionText())
                .questionType(question.getQuestionType().name()).userAnswer(userAnswer)
                .correctAnswer(question.getCorrectAnswer()).isCorrect(correct).optionsJson(optionsSnapshot)
                .build();
    }

    private static String optionsJson(List<QuestionOption> options, Long questionId, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(options.stream()
                    .map(o -> Map.of("label", o.getLabel(), "content", o.getContent()))
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.warn("Failed to serialize options for question {}: {}", questionId, e.getMessage());
            return null;
        }
    }
}
