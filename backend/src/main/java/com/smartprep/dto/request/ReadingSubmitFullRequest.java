package com.smartprep.dto.request;

import jakarta.validation.constraints.NotEmpty;
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

    /** Optional: links to ExamAttempt for timer tracking */
    private Long attemptId;

    /** True if auto-submitted when time expired */
    private Boolean autoSubmitted;
}
