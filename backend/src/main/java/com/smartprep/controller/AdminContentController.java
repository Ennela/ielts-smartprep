package com.smartprep.controller;

import com.smartprep.dto.request.ContentStatusUpdateRequest;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.ContentItemResponse;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.service.ContentModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/content")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ContentModerationService contentModerationService;

    @Operation(summary = "List content with optional type and status filters")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ContentItemResponse>>> listContent(
            @Parameter(description = "Content type: READING, LISTENING, WRITING, MOCK_TEST")
            @RequestParam(required = false) String type,
            @Parameter(description = "Content status: DRAFT, AI_IMPORTED, HUMAN_REVIEWED, PUBLISHED")
            @RequestParam(required = false) ContentStatus status,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        Page<ContentItemResponse> result = contentModerationService.listContent(type, status, page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Update content status with transition validation")
    @PutMapping("/{type}/{id}/status")
    public ResponseEntity<ApiResponse<ContentItemResponse>> updateStatus(
            @Parameter(description = "Content type: READING, LISTENING, WRITING, MOCK_TEST")
            @PathVariable String type,
            @PathVariable Long id,
            @Valid @RequestBody ContentStatusUpdateRequest request) {
        ContentItemResponse result = contentModerationService.updateStatus(type, id, request.getNewStatus());
        return ResponseEntity.ok(ApiResponse.ok(result, "Status updated successfully"));
    }
}
