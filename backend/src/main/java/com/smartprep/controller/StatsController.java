package com.smartprep.controller;

import com.smartprep.dto.response.AnalyticsOverviewResponse;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.ScoreTrendResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * Get analytics overview: skill averages, progress, target comparison.
     * GET /api/v1/stats/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(statsService.getOverview(user.getUserId())));
    }

    /**
     * Get score trend data for charts.
     * GET /api/v1/stats/trend?skill=READING&period=MONTHLY
     */
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<ScoreTrendResponse>> getScoreTrend(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "READING") String skill,
            @RequestParam(defaultValue = "MONTHLY") String period) {
        return ResponseEntity.ok(ApiResponse.ok(
                statsService.getScoreTrend(user.getUserId(), skill, period)));
    }

    /**
     * Get paginated score history.
     * GET /api/v1/stats/history?skill=&page=0&size=10
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                statsService.getHistory(user.getUserId(), skill, page, size)));
    }
}
