package com.smartprep.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSubmitFullRequest {

    @NotEmpty(message = "Quiz IDs cannot be empty")
    private List<Long> quizIds;

    @NotEmpty(message = "Answers cannot be empty")
    private Map<Long, String> answers; // questionId -> userAnswer

    /**
     * The sitting being submitted. Required, because its deadline is what decides
     * whether the answers arrived in time; left optional, a caller could skip that
     * check by not sending it.
     */
    @NotNull(message = "attemptId is required")
    private Long attemptId;

    /** True if auto-submitted when time expired */
    private Boolean autoSubmitted;
}
