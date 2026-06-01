package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 10000, message = "Essay must not exceed 10000 characters")
    private String essayText;
}
