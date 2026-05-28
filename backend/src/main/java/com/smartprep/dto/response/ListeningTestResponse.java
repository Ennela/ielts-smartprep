package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ListeningTestResponse {

    private Long testId;
    private String testMode;
    private BigDecimal score;
    private Integer totalQuestions;
    private Integer correctAnswers;
    private LocalDateTime submittedAt;
    private List<PartResult> parts;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartResult {
        private Long partId;
        private Integer partNumber;
        private String title;
        private String topic;
        private String audioUrl;
        private String transcriptText;
        private List<QuestionResult> questions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionResult {
        private Long questionId;
        private String questionType;
        private String questionText;
        private String correctAnswer;
        private String userAnswer;
        private Boolean isCorrect;
        private Integer orderIndex;
    }
}
