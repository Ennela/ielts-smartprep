package com.smartprep.controller;

import com.smartprep.dto.request.WritingGradeRequest;
import com.smartprep.dto.request.WritingSubmitFullRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.WritingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/writing")
@RequiredArgsConstructor
@Tag(name = "Writing", description = "IELTS Writing practice and AI-grading API")
public class WritingController {

    private final WritingService writingService;

    /**
     * Get all writing prompts, optionally filtered by essay type.
     * GET /api/v1/writing/prompts?essayType=OPINION
     */
    @GetMapping("/prompts")
    @Operation(summary = "Get all writing prompts", description = "Lists writing prompts, optionally filtered by essay type (e.g. OPINION, GRAPH)")
    public ResponseEntity<ApiResponse<List<WritingPromptResponse>>> getPrompts(
            @RequestParam(required = false) String essayType) {
        List<WritingPromptResponse> prompts = writingService.getPrompts(essayType);
        return ResponseEntity.ok(ApiResponse.ok(prompts));
    }

    /**
     * Get a specific writing prompt by ID.
     * GET /api/v1/writing/prompts/{promptId}
     */
    @GetMapping("/prompts/{promptId}")
    @Operation(summary = "Get a specific writing prompt by ID")
    public ResponseEntity<ApiResponse<WritingPromptResponse>> getPromptById(
            @PathVariable Long promptId) {
        WritingPromptResponse prompt = writingService.getPromptById(promptId);
        return ResponseEntity.ok(ApiResponse.ok(prompt));
    }

    /**
     * Submit an essay for AI grading.
     * POST /api/v1/writing/grade
     */
    @PostMapping("/grade")
    @Operation(summary = "Submit an essay for AI grading", description = "Grades a writing task essay using Gemini AI, returning band score and feedback")
    public ResponseEntity<ApiResponse<WritingGradeResponse>> gradeEssay(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WritingGradeRequest request) {
        WritingGradeResponse result = writingService.gradeEssay(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Essay graded successfully"));
    }

    /**
     * Get writing submission history.
     * GET /api/v1/writing/history
     */
    @GetMapping("/history")
    @Operation(summary = "Get writing submission history for current user")
    public ResponseEntity<ApiResponse<List<WritingHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        List<WritingHistoryResponse> history = writingService.getHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    /**
     * Get a specific submission with full grading details.
     * GET /api/v1/writing/submissions/{submissionId}
     */
    @GetMapping("/submissions/{submissionId}")
    @Operation(summary = "Get detailed grading result of a specific submission")
    public ResponseEntity<ApiResponse<WritingGradeResponse>> getSubmission(
            @AuthenticationPrincipal User user,
            @PathVariable Long submissionId) {
        WritingGradeResponse submission = writingService.getSubmission(user.getUserId(), submissionId);
        return ResponseEntity.ok(ApiResponse.ok(submission));
    }

    /**
     * Assemble one random Task 1 prompt and one random Task 2 prompt for a full Writing test.
     * GET /api/v1/writing/assemble
     */
    @GetMapping("/assemble")
    @Operation(summary = "Assemble a full mock Writing test", description = "Selects one Task 1 and one Task 2 prompt at random")
    public ResponseEntity<ApiResponse<List<WritingPromptResponse>>> assemble() {
        List<WritingPromptResponse> prompts = writingService.assembleMockTest();
        return ResponseEntity.ok(ApiResponse.ok(prompts, "Full Writing mock test assembled successfully"));
    }

    /**
     * Submit essays for both Task 1 and Task 2 prompts of a full Writing test.
     * POST /api/v1/writing/submit-full
     */
    @PostMapping("/submit-full")
    @Operation(summary = "Submit a full mock Writing test", description = "Grades both Task 1 and Task 2 essays using Gemini AI")
    public ResponseEntity<ApiResponse<WritingFullResultResponse>> submitFull(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody WritingSubmitFullRequest request) {
        WritingFullResultResponse result = writingService.submitFullWriting(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Full Writing test submitted and graded successfully"));
    }
}
