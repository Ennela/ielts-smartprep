package com.smartprep.dto.response;

import com.smartprep.model.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MockTestSubmissionResponse {
    private Long submissionId;
    private Long mockTestId;
    private String title;
    private SubmissionStatus status;
    private BigDecimal listeningScore;
    private BigDecimal readingScore;
    private BigDecimal writingScore;
    private BigDecimal overallBand;
    private Integer listeningCorrectAnswers;
    private Integer readingCorrectAnswers;
    private LocalDateTime submittedAt;

    private WritingGradeResponse writingTask1;
    private WritingGradeResponse writingTask2;
    private ListeningTestResponse listeningTest;
    private String progressJson; // Includes the mapped answers for verification
}
