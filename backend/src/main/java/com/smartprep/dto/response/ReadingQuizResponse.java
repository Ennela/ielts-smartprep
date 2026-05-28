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
public class ReadingQuizResponse {

    private Long quizId;
    private String topic;
    private String difficulty;
    private String passageText;
    private Integer timeLimitSeconds;
    private boolean submitted;
    private LocalDateTime createdAt;
    private List<QuestionDto> questions;

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
        private Integer orderIndex;
        // correctAnswer and explanation are NOT included here (hidden before submit)
    }
}
