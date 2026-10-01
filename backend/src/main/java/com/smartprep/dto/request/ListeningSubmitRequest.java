package com.smartprep.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ListeningSubmitRequest {

    @NotNull
    private String testMode; // PRACTICE or MOCK_TEST

    @NotEmpty
    private List<Long> partIds;

    /**
     * Map of questionId -> userAnswer
     */
    @NotEmpty
    private Map<Long, String> answers;

    /**
     * The sitting being submitted. Required: it is what limits a sitting to one graded
     * result. While it was optional, leaving it out skipped the only resubmission check,
     * and the response to every submit lists the correct answers.
     */
    @NotNull(message = "attemptId is required")
    private Long attemptId;

    /** True if auto-submitted when time expired */
    private Boolean autoSubmitted;
}
