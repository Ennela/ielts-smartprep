package com.smartprep.controller;

import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.ReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    /**
     * Generate a new reading quiz using AI.
     * POST /api/v1/reading/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ReadingQuizResponse>> generate(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ReadingGenerateRequest request) {
        ReadingQuizResponse quiz = readingService.generateQuiz(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(quiz, "Reading quiz generated successfully"));
    }

    /**
     * Get a quiz by ID.
     * GET /api/v1/reading/{quizId}
     */
    @GetMapping("/{quizId}")
    public ResponseEntity<ApiResponse<ReadingQuizResponse>> getQuiz(
            @AuthenticationPrincipal User user,
            @PathVariable Long quizId) {
        ReadingQuizResponse quiz = readingService.getQuiz(quizId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(quiz));
    }

    /**
     * Submit quiz answers and get results.
     * POST /api/v1/reading/{quizId}/submit
     */
    @PostMapping("/{quizId}/submit")
    public ResponseEntity<ApiResponse<ReadingResultResponse>> submit(
            @AuthenticationPrincipal User user,
            @PathVariable Long quizId,
            @Valid @RequestBody ReadingSubmitRequest request) {
        ReadingResultResponse result = readingService.submitQuiz(quizId, user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Quiz submitted successfully"));
    }

    /**
     * Get reading quiz history.
     * GET /api/v1/reading/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ReadingHistoryResponse>>> history(
            @AuthenticationPrincipal User user) {
        List<ReadingHistoryResponse> historyList = readingService.getHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(historyList));
    }
}
