package com.smartprep.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class HistoryDetailResponse {

    private Long historyId;
    private String skillType;
    private BigDecimal score;
    private LocalDateTime recordedAt;
    private int totalQuestions;
    private int correctCount;
    private List<UserAnswerResponse> answers;
    /**
     * The full mock test sitting this row came from; null for practice. Rows backfilled by
     * V48 carry the link but no answers, so a client seeing this set with an empty list
     * should send the user to the mock test report, where the full review lives.
     */
    private Long mockTestSubmissionId;
}
