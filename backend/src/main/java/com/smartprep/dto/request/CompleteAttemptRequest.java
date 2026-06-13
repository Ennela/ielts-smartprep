package com.smartprep.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteAttemptRequest {

    /** Whether this submission was triggered by timer expiry (auto-submit) */
    private Boolean autoSubmitted;

    /** Writing: actual seconds spent on Task 1 (tracked by FE) */
    private Integer timeSpentTask1;

    /** Writing: actual seconds spent on Task 2 (tracked by FE) */
    private Integer timeSpentTask2;
}
