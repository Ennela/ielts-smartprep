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
public class WritingFullResultResponse {
    private Long id;
    private BigDecimal overallWritingBand;
    private WritingGradeResponse task1Result;
    private WritingGradeResponse task2Result;
    private LocalDateTime submittedAt;
    private Integer timeSpentSeconds;
    private Integer timeSpentTask1;
    private Integer timeSpentTask2;
    private Boolean autoSubmitted;
}
