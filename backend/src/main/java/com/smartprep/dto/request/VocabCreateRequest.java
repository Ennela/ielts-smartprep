package com.smartprep.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabCreateRequest {

    @NotBlank(message = "Word is required")
    private String word;

    private String phonetic;
    private String partOfSpeech;

    @NotBlank(message = "Vietnamese meaning is required")
    private String meaningVi;

    private String example;
    private String collocation;
    private String sourceSkill; // e.g. READING, LISTENING, WRITING, SPEAKING
    private String sourceRef;
    private String cefrLevel;
}
