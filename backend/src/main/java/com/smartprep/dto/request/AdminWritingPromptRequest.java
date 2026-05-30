package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminWritingPromptRequest {
    @NotBlank
    private String promptText;

    @NotNull
    private String essayType;

    private String imageUrl;
}
