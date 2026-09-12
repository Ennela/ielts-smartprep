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
    private String moduleType;
    private String passageText;
    private Integer timeLimitSeconds;
    private boolean submitted;
    private LocalDateTime createdAt;
    /** Question count; on the template list this is all that is known about the questions. */
    private Integer totalQuestions;
    /** Null on the template list -- the questions are only loaded when a quiz is started. */
    private List<QuestionDto> questions;
    private List<Long> quizIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        private Long questionId;
        private String questionType;
        private String questionText;
        private List<QuestionOptionResponse> options;
        private Integer orderIndex;

        // New fields for advanced question types
        private String optionsJson;   // JSON array for matching dropdowns
        private Integer wordLimit;    // word limit for completion types
        private String groupLabel;    // group header text
        private Integer groupId;      // group identifier
        private String groupContext;  // shared context (summary text with blanks)
        // correctAnswer and explanation are NOT included here (hidden before submit)
    }
}
