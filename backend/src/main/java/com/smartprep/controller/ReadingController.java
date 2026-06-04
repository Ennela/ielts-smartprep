package com.smartprep.controller;

import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.dto.request.ReadingSubmitFullRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.ReadingAssemblyService;
import com.smartprep.service.ReadingGradingService;
import com.smartprep.service.ReadingQueryService;
import com.smartprep.service.ai.ReadingGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reading")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingGenerationService readingGenerationService;
    private final ReadingGradingService readingGradingService;
    private final ReadingQueryService readingQueryService;
    private final ReadingAssemblyService readingAssemblyService;

    /**
     * Generate a new reading quiz using AI.
     * POST /api/v1/reading/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ReadingQuizResponse>> generate(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ReadingGenerateRequest request) {
        ReadingQuizResponse quiz = readingGenerationService.generateQuiz(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(quiz, "Reading quiz generated successfully"));
    }

    /**
     * Get available admin-provided reading quiz templates.
     * GET /api/v1/reading/templates
     */
    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<Page<ReadingQuizResponse>>> getTemplates(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReadingQuizResponse> templates = readingQueryService.getTemplateList(topic, difficulty, page, size);
        return ResponseEntity.ok(ApiResponse.ok(templates));
    }

    /**
     * Start a quiz attempt from an admin template.
     * POST /api/v1/reading/templates/{templateId}/start
     */
    @PostMapping("/templates/{templateId}/start")
    public ResponseEntity<ApiResponse<ReadingQuizResponse>> startTemplate(
            @AuthenticationPrincipal User user,
            @PathVariable Long templateId) {
        ReadingQuizResponse quiz = readingAssemblyService.startTemplateQuiz(user.getUserId(), templateId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(quiz, "Reading quiz template started successfully"));
    }

    /**
     * Get a quiz by ID.
     * GET /api/v1/reading/{quizId}
     */
    @GetMapping("/{quizId}")
    public ResponseEntity<ApiResponse<ReadingQuizResponse>> getQuiz(
            @AuthenticationPrincipal User user,
            @PathVariable Long quizId) {
        ReadingQuizResponse quiz = readingQueryService.getQuiz(quizId, user.getUserId());
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
        ReadingResultResponse result = readingGradingService.submitQuiz(quizId, user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Quiz submitted successfully"));
    }

    /**
     * Get quiz result (for submitted quizzes only).
     * GET /api/v1/reading/{quizId}/result
     */
    @GetMapping("/{quizId}/result")
    public ResponseEntity<ApiResponse<ReadingResultResponse>> getResult(
            @AuthenticationPrincipal User user,
            @PathVariable Long quizId) {
        ReadingResultResponse result = readingQueryService.getResult(quizId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Get reading quiz history.
     * GET /api/v1/reading/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ReadingHistoryResponse>>> history(
            @AuthenticationPrincipal User user) {
        List<ReadingHistoryResponse> historyList = readingQueryService.getHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(historyList));
    }

    /**
     * Assemble 3 passages for a full Reading test practice session.
     * GET /api/v1/reading/assemble
     */
    @GetMapping("/assemble")
    public ResponseEntity<ApiResponse<List<ReadingQuizResponse>>> assemble(
            @AuthenticationPrincipal User user) {
        List<ReadingQuizResponse> quizzes = readingAssemblyService.assembleMockTest(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(quizzes, "Full Reading mock test assembled successfully"));
    }

    /**
     * Submit answers for a full Reading test session.
     * POST /api/v1/reading/submit-full
     */
    @PostMapping("/submit-full")
    public ResponseEntity<ApiResponse<ReadingFullResultResponse>> submitFull(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ReadingSubmitFullRequest request) {
        ReadingFullResultResponse result = readingGradingService.submitFullQuiz(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Full Reading test submitted successfully"));
    }
}
