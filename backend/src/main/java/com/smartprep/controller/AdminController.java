package com.smartprep.controller;

import com.smartprep.dto.request.AdminWritingPromptRequest;
import com.smartprep.dto.response.AdminDashboardResponse;
import com.smartprep.dto.response.AdminUserDetailResponse;
import com.smartprep.dto.response.AdminUserResponse;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ===== Dashboard =====

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getDashboardStats()));
    }

    // ===== Users =====

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AdminUserResponse> result = adminService.listUsers(search, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable Long userId) {
        AdminUserDetailResponse detail = adminService.getUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    // ===== Writing Prompts =====

    @GetMapping("/writing-prompts")
    public ResponseEntity<ApiResponse<Page<WritingPrompt>>> listWritingPrompts(
            @RequestParam(required = false) String essayType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listWritingPrompts(essayType, page, size)));
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
}
