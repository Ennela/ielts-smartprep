package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingGradeResponse {

    private Long submissionId;
    private Long promptId;
    private String promptText;
    private String essayType;
    private String essayText;
    private Integer wordCount;

    // Scores
    private BigDecimal overallBand;
    private BigDecimal taskResponse;
    private BigDecimal coherence;
    private BigDecimal lexical;
    private BigDecimal grammar;

    // Error analysis
    private List<ErrorDto> errors;
    private String generalFeedback;

    // Rewritten essay
    private String rewrittenVersion;
    private List<String> improvementNotes;

    private LocalDateTime submittedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDto {
        private String originalSentence;
        private String errorType;
        private String explanation;
        private String correctedSentence;
    }
}
