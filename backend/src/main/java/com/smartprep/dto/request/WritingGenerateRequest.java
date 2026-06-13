package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WritingGenerateRequest {

    private String topic; // e.g. EDUCATION, TECHNOLOGY, HEALTH, etc.

    @NotBlank(message = "Difficulty is required")
    private String difficulty; // EASY, MEDIUM, HARD

    private String moduleType = "ACADEMIC"; // ACADEMIC, GENERAL_TRAINING
}
