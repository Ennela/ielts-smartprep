package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingResultResponse {

    private Long quizId;
    private String topic;
    private String difficulty;
    private String passageText;
    private Integer correctAnswers;
    private Integer totalQuestions;
    private BigDecimal bandScore;
    private List<QuestionResultDto> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResultDto {
        private Long questionId;
        private String questionType;
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private Integer orderIndex;
        private String correctAnswer;
        private String userAnswer;
        private boolean correct;
        private String explanation;

        // New fields for advanced question types
        private String optionsJson;
        private Integer wordLimit;
        private String groupLabel;
        private Integer groupId;
        private String groupContext;
    }
}
