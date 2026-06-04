package com.smartprep.controller;

import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.MockTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Mock Test", description = "IELTS Full Mock Test (Listening, Reading, Writing) API")
public class MockTestController {

    private final MockTestService mockTestService;

    /**
     * Get all available Mock Tests.
     * GET /api/v1/mock-tests
     */
    @GetMapping
    @Operation(summary = "Get all available Mock Tests")
    public ResponseEntity<ApiResponse<List<MockTestResponse>>> getAllMockTests() {
        List<MockTestResponse> tests = mockTestService.getAllMockTests();
        return ResponseEntity.ok(ApiResponse.ok(tests));
    }

    /**
     * Start/Resume Mock Test Session (Aliased).
     * POST /api/v1/mock-tests
     */
    @PostMapping
    @Operation(summary = "Start or resume a mock test session via body payload")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> startMockTestPost(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, Long> payload) {
        Long mockTestId = 1L; // default
        if (payload != null && payload.containsKey("mockTestId")) {
            mockTestId = payload.get("mockTestId");
        }
        MockTestSessionResponse session = mockTestService.startOrResumeSession(user.getUserId(), mockTestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(session, "Mock test session initiated successfully"));
    }

    /**
     * Submit current section progress and transition to next section (Aliased).
     * POST /api/v1/mock-tests/{id}/submit-section
     */
    @PostMapping("/{id}/submit-section")
    @Operation(summary = "Submit section and transition to the next section")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> submitSection(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long sessionId,
            @Valid @RequestBody MockTestProgressRequest request) {
        MockTestSessionResponse session = mockTestService.nextSection(user.getUserId(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.ok(session, "Section transitioned successfully"));
    }

    /**
     * Finish and submit exam for evaluation (Aliased).
     * POST /api/v1/mock-tests/{id}/finish
     */
    @PostMapping("/{id}/finish")
    @Operation(summary = "Finish and submit mock test for grading")
    public ResponseEntity<ApiResponse<MockTestSubmissionResponse>> finishExam(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long sessionId,
            @Valid @RequestBody MockTestSubmitRequest request) {
        MockTestSubmissionResponse submission = mockTestService.submitExam(user.getUserId(), sessionId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(submission, "Exam submitted successfully. AI evaluation is grading."));
    }

    /**
     * Get mock test session by ID (Aliased).
     * GET /api/v1/mock-tests/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get mock test session by ID")
    public ResponseEntity<ApiResponse<MockTestSessionResponse>> getSessionById(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id) {
        MockTestSessionResponse session = mockTestService.getSessionById(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok(session));
    }

    /**
     * Start or resume a session for a specific mock test.
     * POST /api/v1/mock-tests/{id}/start
     */
    @PostMapping("/{id}/start")
    @Operation(summary = "Start or resume a mock test session")
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
    @Operation(summary = "Get active mock test session", description = "Fetches the current active test session if it exists")
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
    @Operation(summary = "Autosave exam session progress", description = "Persists user's current answers and remaining duration")
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
    @Operation(summary = "Transition to the next exam section")
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
    @Operation(summary = "Submit mock test for grading", description = "Submits all sections, initiating asynchronous AI writing grading")
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
    @Operation(summary = "Get detailed mock test submission report")
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
    @Operation(summary = "Get mock test evaluation grading status")
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
    @Operation(summary = "Get user's mock test submission history")
    public ResponseEntity<ApiResponse<List<MockTestHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        List<MockTestHistoryResponse> history = mockTestService.getHistory(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
