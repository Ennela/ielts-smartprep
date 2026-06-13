package com.smartprep.dto.request;

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

    /** Optional override for custom-length practice tests (in seconds) */
    private Integer durationOverride;
}
