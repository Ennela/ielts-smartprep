package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ListeningHistoryResponse {
    private Long testId;
    private String testMode;
    private BigDecimal score;
    private Integer totalQuestions;
    private Integer correctAnswers;
    private LocalDateTime submittedAt;
}
