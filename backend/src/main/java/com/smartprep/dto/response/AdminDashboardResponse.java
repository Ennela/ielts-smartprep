package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long testsToday;
    private long pendingWritings;
    private boolean apiHealthy;
}
