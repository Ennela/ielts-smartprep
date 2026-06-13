package com.smartprep.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WritingSubmitFullRequest {

    @NotNull(message = "Task 1 prompt ID is required")
    private Long task1PromptId;

    @NotNull(message = "Task 1 essay text is required")
    private String task1EssayText;

    @NotNull(message = "Task 2 prompt ID is required")
    private Long task2PromptId;

    @NotNull(message = "Task 2 essay text is required")
    private String task2EssayText;

    /** Optional: links to ExamAttempt for timer tracking */
    private Long attemptId;

    /** True if auto-submitted when time expired */
    private Boolean autoSubmitted;

    /** Seconds spent on Task 1 (tracked by FE) */
    private Integer timeSpentTask1;

    /** Seconds spent on Task 2 (tracked by FE) */
    private Integer timeSpentTask2;
}
