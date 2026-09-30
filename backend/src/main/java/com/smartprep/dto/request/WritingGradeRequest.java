package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WritingGradeRequest {

    @NotNull(message = "Prompt ID is required")
    private Long promptId;

    @NotBlank(message = "Essay text is required")
    @Size(max = 10000, message = "Essay must not exceed 10000 characters")
    private String essayText;

    /**
     * True when the timer ran out and the page submitted on the candidate's behalf.
     * Optional, so an older client that never sends it keeps the old behaviour.
     */
    private Boolean autoSubmitted;

    /** A submission the candidate made themselves, which is every one but the timer's. */
    public WritingGradeRequest(Long promptId, String essayText) {
        this(promptId, essayText, null);
    }
}
