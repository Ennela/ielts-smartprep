package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ListeningPartResponse {
    private Long partId;
    private Integer partNumber;
    private String title;
    private String topic;
    private String audioUrl;
    private Integer durationSeconds;
    private Integer questionCount;
    private List<QuestionDto> questions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionDto {
        private Long questionId;
        private String questionType;
        private String questionText;
        private List<QuestionOptionResponse> options;
        private Integer orderIndex;
    }
}
