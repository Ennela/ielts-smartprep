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

    /** Optional: links to ExamAttempt for timer tracking */
    private Long attemptId;

    /** True if auto-submitted when time expired */
    private Boolean autoSubmitted;
}
