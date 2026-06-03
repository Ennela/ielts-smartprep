package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminListeningStatsResponse {
    private Map<String, Long> statusCounts;
    private Map<String, Long> topicCounts;
    private long totalParts;
}
