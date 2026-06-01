package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AnalyticsOverviewResponse {
    private List<SkillProgress> skills;
    private long totalTests;
    private long targetMetCount;
    private BigDecimal targetBand;
    private BigDecimal currentEstimate;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SkillProgress {
        private String skill;
        private BigDecimal currentAvg;
        private BigDecimal targetScore;
        private double progressPercent;
        private long totalTests;
        private String status;       // TARGET_MET or IN_PROGRESS
        private BigDecimal gap;      // remaining gap to target
    }
}
