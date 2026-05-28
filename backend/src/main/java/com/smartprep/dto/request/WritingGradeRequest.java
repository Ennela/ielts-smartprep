package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String essayText;
}
