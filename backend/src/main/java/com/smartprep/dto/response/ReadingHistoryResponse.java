package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingHistoryResponse {

    private Long quizId;
    private String topic;
    private String difficulty;
    private BigDecimal bandScore;
    private Integer correctAnswers;
    private Integer totalQuestions;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
}
