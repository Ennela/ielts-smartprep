package com.smartprep.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReadingQuizRequest {

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;

    @NotBlank(message = "Passage text is required")
    private String passageText;

    @NotNull(message = "Time limit is required")
    private Integer timeLimitSeconds;

    @NotEmpty(message = "Questions list cannot be empty")
    @Valid
    private List<QuestionRequest> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionRequest {
        private Long questionId;

        @NotBlank(message = "Question type is required")
        private String questionType;

        @NotBlank(message = "Question text is required")
        private String questionText;

        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;

        @NotBlank(message = "Correct answer is required")
        private String correctAnswer;

        private String explanation;

        @NotNull(message = "Order index is required")
        private Integer orderIndex;

        private String optionsJson;
        private Integer wordLimit;
        private String groupLabel;
        private Integer groupId;
        private String groupContext;
    }
}
