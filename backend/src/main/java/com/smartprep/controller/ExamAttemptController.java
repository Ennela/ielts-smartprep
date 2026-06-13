package com.smartprep.controller;

import com.smartprep.dto.request.CompleteAttemptRequest;
import com.smartprep.dto.request.StartAttemptRequest;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.AttemptResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.ExamAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attempts")
@RequiredArgsConstructor
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;

    /**
     * Start a new exam attempt (or resume an existing one).
     * POST /api/v1/attempts/start
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<AttemptResponse>> startAttempt(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody StartAttemptRequest request) {
        AttemptResponse response = examAttemptService.startAttempt(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Exam attempt started"));
    }

    /**
     * Get an existing attempt (for resume on page reload).
     * GET /api/v1/attempts/{attemptId}
     */
    @GetMapping("/{attemptId}")
    public ResponseEntity<ApiResponse<AttemptResponse>> getAttempt(
            @AuthenticationPrincipal User user,
            @PathVariable Long attemptId) {
        AttemptResponse response = examAttemptService.getAttempt(attemptId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Complete an attempt (mark as submitted).
     * POST /api/v1/attempts/{attemptId}/complete
     */
    @PostMapping("/{attemptId}/complete")
    public ResponseEntity<ApiResponse<AttemptResponse>> completeAttempt(
            @AuthenticationPrincipal User user,
            @PathVariable Long attemptId,
            @RequestBody(required = false) CompleteAttemptRequest request) {
        AttemptResponse response = examAttemptService.completeAttempt(
                attemptId, user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Exam attempt completed"));
    }
}
