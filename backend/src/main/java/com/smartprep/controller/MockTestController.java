package com.smartprep.controller;

import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.MockTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mock-tests")
@RequiredArgsConstructor
public class MockTestController {

    private final MockTestService mockTestService;

    /**
     * Get all available Mock Tests.
     * GET /api/v1/mock-tests
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MockTestResponse>>> getAllMockTests() {
        List<MockTestResponse> tests = mockTestService.getAllMockTests();
        return ResponseEntity.ok(ApiResponse.ok(tests));
    }

    /**
     * Start or resume a session for a specific mock test.
     * POST /api/v1/mock-tests/{id}/start
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> startMockTest(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        MockTestSessionResponse session = mockTestService.startOrResumeSession(user.getUserId(), id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(session, "Mock test session initiated successfully"));
    }

    /**
     * Get the current active session of the user.
     * GET /api/v1/mock-tests/sessions/current
     */
    @GetMapping("/sessions/current")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> getCurrentSession(
            @AuthenticationPrincipal User user) {
        MockTestSessionResponse session = mockTestService.getActiveSession(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(session));
    }

    /**
     * Autosave intermediate exam progress.
     * PUT /api/v1/mock-tests/sessions/{sessionId}/progress
     */
    @PutMapping("/sessions/{sessionId}/progress")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> saveProgress(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @Valid @RequestBody MockTestProgressRequest request) {
        MockTestSessionResponse session = mockTestService.saveProgress(user.getUserId(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(session, "Progress autosaved successfully"));
    }

    /**
     * Transition to the next exam section (Listening -> Reading -> Writing).
     * POST /api/v1/mock-tests/sessions/{sessionId}/next-section
     */
    @PostMapping("/sessions/{sessionId}/next-section")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> nextSection(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @Valid @RequestBody MockTestProgressRequest request) {
        MockTestSessionResponse session = mockTestService.nextSection(user.getUserId(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(session, "Section transitioned successfully"));
    }

    /**
     * Submit the exam for evaluation.
     * POST /api/v1/mock-tests/sessions/{sessionId}/submit
     */
    @PostMapping("/sessions/{sessionId}/submit")
    public ResponseEntity<ApiResponse<MockTestSubmissionResponse>> submitExam(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @Valid @RequestBody MockTestSubmitRequest request) {
        MockTestSubmissionResponse submission = mockTestService.submitExam(user.getUserId(), sessionId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(submission, "Exam submitted successfully. AI evaluation is grading."));
    }

    /**
     * Get the detailed submission report.
     * GET /api/v1/mock-tests/submissions/{submissionId}
     */
    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<ApiResponse<MockTestSubmissionResponse>> getSubmission(
            @AuthenticationPrincipal User user,
            @PathVariable Long submissionId) {
        MockTestSubmissionResponse submission = mockTestService.getSubmission(user.getUserId(), submissionId);
        return ResponseEntity.ok(ApiResponse.ok(submission));
    }

    /**
     * Check writing evaluation status (polling endpoint).
     * GET /api/v1/mock-tests/submissions/{submissionId}/status
     */
    @GetMapping("/submissions/{submissionId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGradingStatus(
            @AuthenticationPrincipal User user,
            @PathVariable Long submissionId) {
        MockTestSubmissionResponse submission = mockTestService.getSubmission(user.getUserId(), submissionId);
        Map<String, Object> statusMap = Map.of(
                "status", submission.getStatus(),
                "overallBand", submission.getOverallBand()
        );
        return ResponseEntity.ok(ApiResponse.ok(statusMap));
    }

    /**
     * Get user mock test submission history.
     * GET /api/v1/mock-tests/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<MockTestHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        List<MockTestHistoryResponse> history = mockTestService.getHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
