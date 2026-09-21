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
    /** ScoreHistory row of this sitting; the client's fallback when navigation state is lost. */
    private Long historyId;
    private BigDecimal overallBand;
    private int totalCorrect;
    private int totalQuestions;
    private LocalDateTime submittedAt;
    private List<ReadingResultResponse> quizResults;
    private Integer timeSpentSeconds;
    private Boolean autoSubmitted;
}
