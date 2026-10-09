package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A passage written in NotebookLM, as the JSON the Reading prompts ask for, to add to the bank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReadingImportRequest {

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;

    @NotBlank(message = "Content is required")
    private String content;
}
