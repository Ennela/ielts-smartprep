package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A graded Speaking answer. */
@Data
@Builder
public class SpeakingSubmissionResponse {
    private Long submissionId;
    private SpeakingPromptResponse prompt;
    private int durationSeconds;
    /** Part 2 only; Part 1/3 transcripts are per answer. */
    private String transcript;
    private BigDecimal overallBand;
    private BigDecimal fluencyBand;
    private BigDecimal lexicalBand;
    private BigDecimal grammarBand;
    private BigDecimal pronunciationBand;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    /** One comment per criterion, keyed fluencyCoherence / lexicalResource / grammaticalRange / pronunciation. */
    private Map<String, String> criteriaComments;
    /** Part 1 and Part 3: one per question, in order. Empty for Part 2. */
    private List<SpeakingAnswerResponse> answers;
    private LocalDateTime submittedAt;
}
