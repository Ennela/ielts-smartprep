package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReadingQuizResponse {

    private Long quizId;
    private String topic;
    private String difficulty;
    private String passageText;
    private Integer timeLimitSeconds;
    private Integer totalQuestions;
    private LocalDateTime createdAt;
    private List<QuestionDto> questions;
    private Boolean isTemplate;
    private String createdBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private Long questionId;
        private String questionType;
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctAnswer;
        private String explanation;
        private Integer orderIndex;
        private String optionsJson;
        private Integer wordLimit;
        private String groupLabel;
        private Integer groupId;
        private String groupContext;
    }
}
