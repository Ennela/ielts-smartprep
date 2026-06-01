package com.smartprep.dto.response;

import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MockTestSessionResponse {
    private Long sessionId;
    private Long mockTestId;
    private String title;
    private SessionStatus status;
    private SkillType currentSection;
    private Integer timeRemainingSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime sectionStartedAt;
    private LocalDateTime lastSyncedAt;
    private String progressJson;

    // The section details returned depends on currentSection
    private List<ListeningPartResponse> listeningParts;
    private List<ReadingQuizResponse> readingQuizzes;
    private List<WritingPromptResponse> writingPrompts;
}
