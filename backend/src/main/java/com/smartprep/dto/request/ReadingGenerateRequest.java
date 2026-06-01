package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingGenerateRequest {

    @NotBlank(message = "Topic is required")
    @Size(max = 100, message = "Topic must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 &,.'\\-]+$", message = "Topic contains invalid characters")
    private String topic;

    @NotBlank(message = "Difficulty is required")
    @Size(max = 20, message = "Difficulty must not exceed 20 characters")
    private String difficulty;
}
