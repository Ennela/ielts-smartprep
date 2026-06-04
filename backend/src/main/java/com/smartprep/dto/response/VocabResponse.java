package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VocabResponse {
    private Long vocabId;
    private String word;
    private String phonetic;
    private String partOfSpeech;
    private String meaningVi;
    private String example;
    private String collocation;
    private Double easeFactor;
    private Integer intervalDays;
    private Integer repetitions;
    private LocalDateTime dueDate;
    private String sourceSkill;
    private String sourceRef;
    private String cefrLevel;
}
