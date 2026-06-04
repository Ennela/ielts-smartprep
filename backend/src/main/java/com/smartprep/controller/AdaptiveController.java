package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.service.AdaptiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adaptive")
@RequiredArgsConstructor
@Tag(name = "Adaptive Learning", description = "Endpoints for adaptive quiz recommendation and capability mapping")
public class AdaptiveController {

    private final AdaptiveService adaptiveService;

    public static class AdaptiveConfigResponse {
        public double estimatedBand;
        public String targetDifficulty;
        public String focusQuestionType;
        public String reason;
    }

    @GetMapping("/next")
    @Operation(summary = "Get proposed adaptive quiz configuration", description = "Calculates estimated band capability, next difficulty level, and focus question type based on historical attempts.")
    public ResponseEntity<ApiResponse<AdaptiveConfigResponse>> getNextRecommendation(
            @AuthenticationPrincipal User user,
            @RequestParam String skill) {

        SkillType skillType = SkillType.valueOf(skill.toUpperCase().trim());
        AdaptiveService.AdaptiveConfig config = adaptiveService.suggestNextConfig(user.getUserId(), skillType);

        AdaptiveConfigResponse response = new AdaptiveConfigResponse();
        response.estimatedBand = config.estimatedBand;
        response.targetDifficulty = config.nextDifficulty;
        response.focusQuestionType = config.focusQuestionType;
        response.reason = config.reason;

        return ResponseEntity.ok(ApiResponse.ok(response, "Adaptive recommendation generated"));
    }
}
