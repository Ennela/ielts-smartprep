package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptResponse {

    private Long attemptId;
    private String skillType;
    private Integer durationSeconds;
    /**
     * ISO-8601 with the server's UTC offset. The entity stores LocalDateTime and an
     * earlier version sent it as-is; the browser parsed that as its own local time, so
     * a candidate outside UTC+7 saw the timer off by their whole offset.
     */
    private OffsetDateTime startedAt;
    private OffsetDateTime deadline;
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
