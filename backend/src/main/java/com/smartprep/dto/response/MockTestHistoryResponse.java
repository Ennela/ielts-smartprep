package com.smartprep.dto.response;

import com.smartprep.model.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MockTestHistoryResponse {
    private Long submissionId;
    private Long mockTestId;
    private String title;
    private SubmissionStatus status;
    private BigDecimal overallBand;
    private LocalDateTime submittedAt;
}
