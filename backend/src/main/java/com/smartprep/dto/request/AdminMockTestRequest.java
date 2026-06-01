package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class AdminMockTestRequest {
    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String difficulty;

    @NotNull
    private List<Long> listeningPartIds;

    @NotNull
    private List<Long> readingQuizIds;

    @NotNull
    private List<Long> writingPromptIds;
}
