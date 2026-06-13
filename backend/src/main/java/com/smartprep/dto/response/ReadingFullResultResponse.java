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
public class ReadingFullResultResponse {
    private BigDecimal overallBand;
    private int totalCorrect;
    private int totalQuestions;
    private LocalDateTime submittedAt;
    private List<ReadingResultResponse> quizResults;
    private Integer timeSpentSeconds;
    private Boolean autoSubmitted;
}
