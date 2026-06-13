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
public class WritingHistoryResponse {

    private Long submissionId;
    private Long promptId;
    private String essayType;
    private String promptTextPreview; // first 100 chars
    private BigDecimal overallBand;
    private Integer wordCount;
    private LocalDateTime submittedAt;
    private Integer timeSpentSeconds;
    private Boolean autoSubmitted;
}
