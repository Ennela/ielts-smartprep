package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Endpoints for user progress tracking, trends, and weaknesses")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get analytics overview statistics", description = "Retrieves total tests, average scores, target scores, and progress tips.")
    public ResponseEntity<ApiResponse<AnalyticsService.OverviewDto>> getOverview(
            @AuthenticationPrincipal User user) {
        AnalyticsService.OverviewDto data = analyticsService.getOverview(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(data, "Overview analytics retrieved successfully"));
    }

    @GetMapping("/score-trend")
    @Operation(summary = "Get historical score trends", description = "Retrieves date and band score pairs chronologically for a specific skill.")
    public ResponseEntity<ApiResponse<List<AnalyticsService.TrendPointDto>>> getScoreTrend(
            @AuthenticationPrincipal User user,
            @RequestParam String skill) {
        SkillType skillType = SkillType.valueOf(skill.toUpperCase().trim());
        List<AnalyticsService.TrendPointDto> data = analyticsService.getScoreTrend(user.getUserId(), skillType);
        return ResponseEntity.ok(ApiResponse.ok(data, "Score trend retrieved successfully"));
    }

    @GetMapping("/weakness")
    @Operation(summary = "Get question type accuracy & weakness", description = "Retrieves correctness rates per question type and identifies the weakest type.")
    public ResponseEntity<ApiResponse<AnalyticsService.WeaknessDto>> getWeakness(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String skill) {
        SkillType skillType = (skill != null && !skill.isBlank()) 
                ? SkillType.valueOf(skill.toUpperCase().trim()) 
                : null;
        AnalyticsService.WeaknessDto data = analyticsService.getWeakness(user.getUserId(), skillType);
        return ResponseEntity.ok(ApiResponse.ok(data, "Weakness analytics retrieved successfully"));
    }
}
