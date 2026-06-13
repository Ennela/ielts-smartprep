package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponse {

    private Long attemptId;
    private String skillType;
    private Integer durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime deadline;
    private String status;
    private Boolean autoSubmitted;
    private Integer timeSpentSeconds;
    private Integer timeSpentTask1;
    private Integer timeSpentTask2;
    private String examReferenceIds;

    /** Suggested durations for Writing tasks (only populated for WRITING skill) */
    private Integer suggestedTask1Duration;
    private Integer suggestedTask2Duration;
}
