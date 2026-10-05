package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the Speaking history list. */
@Data
@Builder
public class SpeakingHistoryItemResponse {
    private Long submissionId;
    private int part;
    private String topic;
    private BigDecimal overallBand;
    private int durationSeconds;
    private LocalDateTime submittedAt;
}
