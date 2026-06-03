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
}
