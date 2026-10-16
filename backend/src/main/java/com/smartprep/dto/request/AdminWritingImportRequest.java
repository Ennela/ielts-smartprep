package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Prompts written in NotebookLM, as the {task1, task2} JSON the Writing prompt asks for, to add to the bank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWritingImportRequest {

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotBlank(message = "Content is required")
    private String content;
}
