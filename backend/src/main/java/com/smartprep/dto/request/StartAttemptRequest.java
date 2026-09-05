package com.smartprep.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartAttemptRequest {

    @NotNull(message = "Skill type is required")
    private String skillType; // READING, LISTENING, WRITING

    /** JSON string of reference IDs (quizIds, partIds, or promptIds) */
    private String examReferenceIds;

    /**
     * Optional override for custom-length practice tests (in seconds).
     * May only shorten an exam — the server clamps it to the skill's standard
     * duration in {@code ExamDurationConfig.getEffectiveDuration}.
     */
    @Min(value = 60, message = "Duration override must be at least 60 seconds")
    @Max(value = 14400, message = "Duration override must not exceed 4 hours")
    private Integer durationOverride;
}
