package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabAiSuggestRequest {

    @NotBlank(message = "Skill type is required")
    private String skillType; // READING, WRITING, LISTENING, SPEAKING

    @NotNull(message = "Source ID is required")
    private Long sourceId;
}
