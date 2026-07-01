package com.smartprep.controller;

import com.smartprep.dto.request.AdminWritingPromptRequest;
import com.smartprep.dto.request.AdminReadingQuizRequest;
import com.smartprep.dto.request.AdminMockTestRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;

    // ===== Dashboard =====

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getDashboardStats()));
    }

    // ===== Users =====

    @Operation(summary = "List users with pagination and optional search")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @Parameter(description = "Search by username or email (optional)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction, e.g. 'createdAt,desc'", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        Page<AdminUserResponse> result = adminService.listUsers(search, page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable Long userId) {
        AdminUserDetailResponse detail = adminService.getUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    // ===== Writing Prompts =====

    @Operation(summary = "List writing prompts with pagination and optional essay type filter")
    @GetMapping("/writing-prompts")
    public ResponseEntity<ApiResponse<Page<WritingPrompt>>> listWritingPrompts(
            @Parameter(description = "Filter by essay type, e.g. OPINION, DISCUSSION")
            @RequestParam(required = false) String essayType,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.listWritingPrompts(essayType, page, size, sort)));
    }

    @PostMapping("/writing-prompts")
    public ResponseEntity<ApiResponse<WritingPrompt>> createWritingPrompt(
            @Valid @RequestBody AdminWritingPromptRequest request) {
        WritingPrompt created = adminService.createWritingPrompt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Prompt created"));
    }

    @PutMapping("/writing-prompts/{promptId}")
    public ResponseEntity<ApiResponse<WritingPrompt>> updateWritingPrompt(
            @PathVariable Long promptId,
            @Valid @RequestBody AdminWritingPromptRequest request) {
        WritingPrompt updated = adminService.updateWritingPrompt(promptId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Prompt updated"));
    }

    @DeleteMapping("/writing-prompts/{promptId}")
    public ResponseEntity<ApiResponse<Void>> deleteWritingPrompt(@PathVariable Long promptId) {
        adminService.deleteWritingPrompt(promptId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Prompt deleted"));
    }

    // ===== Reading Quizzes =====

    @Operation(summary = "List reading quizzes with pagination and filters")
    @GetMapping("/reading-quizzes")
    public ResponseEntity<ApiResponse<Page<AdminReadingQuizResponse>>> listReadingQuizzes(
            @Parameter(description = "Filter by topic") @RequestParam(required = false) String topic,
            @Parameter(description = "Filter by difficulty") @RequestParam(required = false) String difficulty,
            @Parameter(description = "Filter by source: ADMIN or AI") @RequestParam(required = false) String source,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.listReadingQuizzes(topic, difficulty, source, page, size, sort)));
    }

    @PostMapping("/reading-quizzes")
    public ResponseEntity<ApiResponse<AdminReadingQuizResponse>> createReadingQuiz(
            @Valid @RequestBody AdminReadingQuizRequest request) {
        AdminReadingQuizResponse created = adminService.createReadingQuiz(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Reading quiz template created"));
    }

    @PutMapping("/reading-quizzes/{quizId}")
    public ResponseEntity<ApiResponse<AdminReadingQuizResponse>> updateReadingQuiz(
            @PathVariable Long quizId,
            @Valid @RequestBody AdminReadingQuizRequest request) {
        AdminReadingQuizResponse updated = adminService.updateReadingQuiz(quizId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Reading quiz template updated"));
    }

    @DeleteMapping("/reading-quizzes/{quizId}")
    public ResponseEntity<ApiResponse<Void>> deleteReadingQuiz(@PathVariable Long quizId) {
        adminService.deleteReadingQuiz(quizId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Reading quiz template deleted"));
    }

    // ===== Mock Tests =====

    @Operation(summary = "List mock tests with pagination")
    @GetMapping("/mock-tests")
    public ResponseEntity<ApiResponse<Page<MockTestResponse>>> listMockTests(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        return ResponseEntity.ok(ApiResponse.ok(adminService.listMockTests(page, size, sort)));
    }

    @PostMapping("/mock-tests")
    public ResponseEntity<ApiResponse<MockTestResponse>> createMockTest(
            @Valid @RequestBody AdminMockTestRequest request) {
        MockTestResponse created = adminService.createMockTest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Mock test created successfully"));
    }

    @PutMapping("/mock-tests/{mockTestId}")
    public ResponseEntity<ApiResponse<MockTestResponse>> updateMockTest(
            @PathVariable Long mockTestId,
            @Valid @RequestBody AdminMockTestRequest request) {
        MockTestResponse updated = adminService.updateMockTest(mockTestId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Mock test updated successfully"));
    }

    @DeleteMapping("/mock-tests/{mockTestId}")
    public ResponseEntity<ApiResponse<Void>> deleteMockTest(@PathVariable Long mockTestId) {
        adminService.deleteMockTest(mockTestId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mock test deleted successfully"));
    }

    @GetMapping("/reading/{quizId}/preview")
    public ResponseEntity<ApiResponse<AdminReadingQuizResponse>> getReadingPreview(@PathVariable Long quizId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getReadingQuizForPreview(quizId)));
    }

    @GetMapping("/writing/{promptId}/preview")
    public ResponseEntity<ApiResponse<WritingPrompt>> getWritingPreview(@PathVariable Long promptId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getWritingPromptForPreview(promptId)));
    }
}
