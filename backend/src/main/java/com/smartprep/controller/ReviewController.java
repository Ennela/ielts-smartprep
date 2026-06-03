package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.HistoryDetailResponse;
import com.smartprep.dto.response.UserAnswerResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Get detailed review of a history entry including all user answers.
     * GET /api/v1/history/{historyId}/answers
     */
    @GetMapping("/{historyId}/answers")
    public ResponseEntity<ApiResponse<HistoryDetailResponse>> getHistoryDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Long historyId) {
        HistoryDetailResponse detail = reviewService.getHistoryDetail(historyId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * Generate AI explanation for a specific answer.
     * POST /api/v1/history/{historyId}/answers/{answerId}/explain
     */
    @PostMapping("/{historyId}/answers/{answerId}/explain")
    public ResponseEntity<ApiResponse<UserAnswerResponse>> explainAnswer(
            @AuthenticationPrincipal User user,
            @PathVariable Long historyId,
            @PathVariable Long answerId) {
        UserAnswerResponse response = reviewService.explainAnswer(historyId, answerId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response, "Explanation generated"));
    }
}
