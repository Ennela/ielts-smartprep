package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ScoreTrendResponse {
    private String skill;
    private String period;
    private BigDecimal targetScore;
    private List<DataPoint> dataPoints;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DataPoint {
        private String period;
        private BigDecimal avgScore;
    }
}
