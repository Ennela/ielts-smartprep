package com.smartprep.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A part written in NotebookLM, as the JSON the Listening prompt asks for, to add to the bank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminListeningImportRequest {

    @NotNull(message = "Part number is required")
    @Min(1) @Max(4)
    private Integer partNumber;

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotBlank(message = "Content is required")
    private String content;
}
