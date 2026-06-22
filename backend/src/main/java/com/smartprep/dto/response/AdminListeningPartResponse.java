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
public class AdminListeningPartResponse {
    private Long partId;
    private Integer partNumber;
    private String title;
    private String topic;
    private String audioUrl;
    private String audioStatus;
    private Integer durationSeconds;
    private String transcriptText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private Integer questionCount;
    private List<QuestionDto> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private Long questionId;
        private Boolean verified;
        private String questionType;
        private String questionText;
        private String correctAnswer;
        private Integer orderIndex;
        private List<QuestionOptionResponse> options;
    }
}
