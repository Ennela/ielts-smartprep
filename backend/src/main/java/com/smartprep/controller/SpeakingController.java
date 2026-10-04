package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.SpeakingHistoryItemResponse;
import com.smartprep.dto.response.SpeakingPromptResponse;
import com.smartprep.dto.response.SpeakingSubmissionResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.SpeakingService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/speaking")
@RequiredArgsConstructor
public class SpeakingController {

    private final SpeakingService speakingService;

    @GetMapping("/prompts")
    @Operation(summary = "List Speaking prompts, optionally for one part (1, 2 or 3)")
    public ResponseEntity<ApiResponse<List<SpeakingPromptResponse>>> prompts(
            @RequestParam(required = false) Integer part) {
        return ResponseEntity.ok(ApiResponse.ok(speakingService.listPrompts(part)));
    }

    /**
     * Grade a recorded answer. Its own path, not POST /submissions, so the AI rate limit
     * (WebMvcConfig) meters grading without also metering the history list.
     */
    @PostMapping(value = "/grade", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Grade a recorded Speaking answer", description = "webm/ogg/mp4/mp3/wav up to 5 MB")
    public ResponseEntity<ApiResponse<SpeakingSubmissionResponse>> grade(
            @AuthenticationPrincipal User user,
            @RequestParam("promptId") Long promptId,
            @RequestParam("durationSeconds") int durationSeconds,
            @RequestParam("audio") MultipartFile audio) throws IOException {
        SpeakingSubmissionResponse result = speakingService.grade(
                user.getUserId(), promptId, audio.getBytes(), audio.getContentType(), durationSeconds);
        return ResponseEntity.ok(ApiResponse.ok(result, "Answer graded"));
    }

    @GetMapping("/submissions")
    @Operation(summary = "The current user's Speaking results, newest first")
    public ResponseEntity<ApiResponse<Page<SpeakingHistoryItemResponse>>> history(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(speakingService.history(user.getUserId(), page, size)));
    }

    @GetMapping("/submissions/{submissionId}")
    @Operation(summary = "One graded Speaking answer of the current user")
    public ResponseEntity<ApiResponse<SpeakingSubmissionResponse>> submission(
            @AuthenticationPrincipal User user, @PathVariable Long submissionId) {
        return ResponseEntity.ok(ApiResponse.ok(speakingService.getSubmission(user.getUserId(), submissionId)));
    }

    /** The recording itself, to its owner only (the page fetches it with the bearer token). */
    @GetMapping("/submissions/{submissionId}/audio")
    @Operation(summary = "Play back the current user's recording")
    public ResponseEntity<byte[]> audio(@AuthenticationPrincipal User user, @PathVariable Long submissionId) {
        Map.Entry<byte[], String> recording = speakingService.recording(user.getUserId(), submissionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(recording.getValue()))
                .cacheControl(CacheControl.noStore())
                .body(recording.getKey());
    }
}
