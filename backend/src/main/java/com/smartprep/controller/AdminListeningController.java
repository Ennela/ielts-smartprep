package com.smartprep.controller;

import com.smartprep.dto.request.AdminListeningPartRequest;
import com.smartprep.dto.response.AdminListeningPartResponse;
import com.smartprep.dto.response.AdminListeningStatsResponse;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.AdminListeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/listening")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminListeningController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminListeningService adminListeningService;

    /**
     * GET /api/v1/admin/listening/parts
     * List all listening parts with pagination and filtering.
     */
    @Operation(summary = "List listening parts with pagination and filtering")
    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<Page<AdminListeningPartResponse>>> listParts(
            @Parameter(description = "Filter by audio status: READY, PENDING, FAILED")
            @RequestParam(required = false) String audioStatus,
            @Parameter(description = "Filter by topic")
            @RequestParam(required = false) String topic,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field and direction", example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        size = Math.min(size, MAX_PAGE_SIZE);
        Page<AdminListeningPartResponse> result = adminListeningService.listParts(audioStatus, topic, page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * GET /api/v1/admin/listening/parts/{partId}
     * Get details of a single listening part (includes correct answers).
     */
    @GetMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<AdminListeningPartResponse>> getPartById(@PathVariable Long partId) {
        AdminListeningPartResponse detail = adminListeningService.getPartById(partId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * POST /api/v1/admin/listening/parts
     * Create a new listening part.
     */
    @PostMapping("/parts")
    public ResponseEntity<ApiResponse<AdminListeningPartResponse>> createPart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AdminListeningPartRequest request) {
        String username = (user != null) ? user.getUsername() : "ADMIN";
        AdminListeningPartResponse created = adminListeningService.createPart(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created, "Listening part created successfully"));
    }

    /**
     * PUT /api/v1/admin/listening/parts/{partId}
     * Update an existing listening part.
     */
    @PutMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<AdminListeningPartResponse>> updatePart(
            @AuthenticationPrincipal User user,
            @PathVariable Long partId,
            @Valid @RequestBody AdminListeningPartRequest request) {
        String username = (user != null) ? user.getUsername() : "ADMIN";
        AdminListeningPartResponse updated = adminListeningService.updatePart(partId, request, username);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Listening part updated successfully"));
    }

    /**
     * DELETE /api/v1/admin/listening/parts/{partId}
     * Delete a listening part.
     */
    @DeleteMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<Void>> deletePart(@PathVariable Long partId) {
        adminListeningService.deletePart(partId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Listening part deleted successfully"));
    }

    /**
     * POST /api/v1/admin/listening/parts/{partId}/regenerate-audio
     * Trigger manual regeneration of TTS audio.
     */
    @PostMapping("/parts/{partId}/regenerate-audio")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateAudio(@PathVariable Long partId) {
        adminListeningService.regenerateAudio(partId);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(
                        Map.of("status", "PENDING", "message", "Audio generation started"),
                        "Audio regeneration queued"));
    }

    /**
     * POST /api/v1/admin/listening/parts/retry-failed-audio
     * Retry audio generation for all parts that are currently FAILED.
     */
    @PostMapping("/parts/retry-failed-audio")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryFailedAudio() {
        adminListeningService.retryFailedAudio();
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(
                        Map.of("status", "PENDING", "message", "Retry of failed audio generation started"),
                        "Retry process queued"));
    }

    /**
     * GET /api/v1/admin/listening/stats
     * Get statistics regarding audio status and topic distribution.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminListeningStatsResponse>> getStats() {
        AdminListeningStatsResponse stats = adminListeningService.getStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    /**
     * GET /api/v1/admin/listening/{partId}/preview
     * Get detailed transcript and answers for preview.
     */
    @GetMapping("/{partId}/preview")
    public ResponseEntity<ApiResponse<AdminListeningPartResponse>> getListeningPreview(@PathVariable Long partId) {
        AdminListeningPartResponse detail = adminListeningService.getPartById(partId);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }
}
