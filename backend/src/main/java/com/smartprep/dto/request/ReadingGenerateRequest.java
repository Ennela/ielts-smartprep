package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingGenerateRequest {

    @NotBlank(message = "Topic is required")
    private String topic;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;
}
