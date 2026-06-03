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
public class AdminListeningPartRequest {

    @NotNull(message = "Part number is required")
    private Integer partNumber;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Topic is required")
    private String topic;

    private String transcriptText;

    private Integer durationSeconds;

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

        @NotBlank(message = "Correct answer is required")
        private String correctAnswer;

        @NotNull(message = "Order index is required")
        private Integer orderIndex;

        private List<OptionRequest> options;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class OptionRequest {
            @NotBlank(message = "Option label is required")
            private String label;

            @NotBlank(message = "Option content is required")
            private String content;
        }
    }
}
