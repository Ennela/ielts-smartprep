package com.smartprep.dto.response;

import com.smartprep.model.enums.Difficulty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MockTestResponse {
    private Long mockTestId;
    private String title;
    private String description;
    private Difficulty difficulty;
    private int listeningPartsCount;
    private int readingQuizzesCount;
    private int writingPromptsCount;
    private List<Long> listeningPartIds;
    private List<Long> readingQuizIds;
    private List<Long> writingPromptIds;
}
