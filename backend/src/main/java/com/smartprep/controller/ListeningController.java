package com.smartprep.controller;

import com.smartprep.dto.request.ListeningGenerateRequest;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.ListeningAudioService;
import com.smartprep.service.ListeningGradingService;
import com.smartprep.service.ListeningQueryService;
import com.smartprep.service.StorageService;
import com.smartprep.service.ai.ListeningGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/listening")
@RequiredArgsConstructor
@Slf4j
public class ListeningController {

    private final ListeningGenerationService listeningGenerationService;
    private final ListeningGradingService listeningGradingService;
    private final ListeningQueryService listeningQueryService;
    private final ListeningAudioService listeningAudioService;
    private final StorageService storageService;

    /**
     * Get all listening parts.
     * GET /api/v1/listening/parts
     */
    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<List<ListeningPartResponse>>> getAllParts() {
        return ResponseEntity.ok(ApiResponse.ok(listeningQueryService.getAllParts()));
    }

    /**
     * Get a specific part with questions (no answers).
     * GET /api/v1/listening/parts/{partId}
     */
    @GetMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<ListeningPartResponse>> getPartById(@PathVariable Long partId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningQueryService.getPartById(partId)));
    }

    /**
     * Assemble a random mock test (4 parts, avoid recent).
     * GET /api/v1/listening/mock-test
     */
    @GetMapping("/mock-test")
    public ResponseEntity<ApiResponse<List<ListeningPartResponse>>> assembleMockTest(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(listeningQueryService.assembleMockTest(user.getUserId())));
    }

    /**
     * Submit test answers for grading.
     * POST /api/v1/listening/submit
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ListeningTestResponse>> submitTest(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ListeningSubmitRequest request) {
        ListeningTestResponse result = listeningGradingService.submitTest(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Test graded successfully"));
    }

    /**
     * Get listening test history.
     * GET /api/v1/listening/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ListeningHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(listeningQueryService.getHistory(user.getUserId())));
    }

    /**
     * AI analysis for a specific question.
     * POST /api/v1/listening/ai-analyze/{questionId}
     */
    @PostMapping("/ai-analyze/{questionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeQuestion(
            @PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningGenerationService.analyzeQuestion(questionId)));
    }

    /**
     * AI vocabulary extraction from a part's transcript.
     * POST /api/v1/listening/vocabulary/{partId}
     */
    @PostMapping("/vocabulary/{partId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> extractVocabulary(
            @PathVariable Long partId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningGenerationService.extractVocabulary(partId)));
    }

    /**
     * Generate a new listening part using AI.
     * POST /api/v1/listening/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ListeningPartResponse>> generatePart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ListeningGenerateRequest request) {
        ListeningPartResponse response = listeningGenerationService.generatePart(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Generate a full Listening Mock Test (4 parts) using AI.
     * POST /api/v1/listening/generate-mock
     */
    @PostMapping("/generate-mock")
    public ResponseEntity<ApiResponse<List<ListeningPartResponse>>> generateMock(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) ListeningGenerateRequest request) {
        String topic = request != null ? request.getTopic() : null;
        List<ListeningPartResponse> response = listeningGenerationService.generateFullTest(user.getUserId(), topic);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Manually trigger audio generation for a listening part.
     * POST /api/v1/listening/{partId}/generate-audio
     */
    @PostMapping("/{partId}/generate-audio")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateAudio(@PathVariable Long partId) {
        listeningAudioService.generateAudio(partId);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(
                        Map.of("status", "PENDING", "message", "Audio generation started"),
                        "Audio generation queued"));
    }

    /**
     * Serve listening audio files with Range Request support for seeking.
     * First tries MinIO (real TTS audio), then classpath, then silent fallback.
     * GET /api/v1/listening/audio/{fileName}
     */
    @GetMapping(value = "/audio/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> getAudioFile(
            @PathVariable String fileName,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        byte[] audioBytes = resolveAudioBytes(fileName);

        long totalLength = audioBytes.length;
        org.springframework.http.MediaType contentType =
                org.springframework.http.MediaType.parseMediaType("audio/mpeg");

        // If no Range header, return full content with Accept-Ranges hint
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(totalLength)
                    .header("Accept-Ranges", "bytes")
                    .body(new org.springframework.core.io.ByteArrayResource(audioBytes));
        }

        // Parse Range header: "bytes=start-end" or "bytes=start-"
        try {
            String rangeSpec = rangeHeader.replace("bytes=", "").trim();
            String[] parts = rangeSpec.split("-", 2);
            long start = Long.parseLong(parts[0]);
            long end = (parts.length > 1 && !parts[1].isBlank())
                    ? Long.parseLong(parts[1])
                    : totalLength - 1;

            // Clamp to valid range
            end = Math.min(end, totalLength - 1);
            if (start > end || start >= totalLength) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header("Content-Range", "bytes */" + totalLength)
                        .build();
            }

            int rangeLength = (int) (end - start + 1);
            byte[] sliced = new byte[rangeLength];
            System.arraycopy(audioBytes, (int) start, sliced, 0, rangeLength);

            return ResponseEntity.status(org.springframework.http.HttpStatus.PARTIAL_CONTENT)
                    .contentType(contentType)
                    .contentLength(rangeLength)
                    .header("Accept-Ranges", "bytes")
                    .header("Content-Range", "bytes " + start + "-" + end + "/" + totalLength)
                    .body(new org.springframework.core.io.ByteArrayResource(sliced));
        } catch (NumberFormatException e) {
            log.warn("Invalid Range header '{}' for audio '{}'", rangeHeader, fileName);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(totalLength)
                    .header("Accept-Ranges", "bytes")
                    .body(new org.springframework.core.io.ByteArrayResource(audioBytes));
        }
    }

    /**
     * Resolve audio bytes from MinIO, classpath, or silent fallback.
     */
    private byte[] resolveAudioBytes(String fileName) {
        // Try MinIO first
        try {
            return storageService.downloadAudio(fileName);
        } catch (Exception e) {
            log.debug("Audio not found in MinIO for '{}', trying classpath", fileName);
        }

        // Try classpath static resource
        try {
            org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource("static/api/v1/listening/audio/" + fileName);
            if (resource.exists()) {
                return resource.getInputStream().readAllBytes();
            }
        } catch (Exception e) {
            // Fall through
        }

        // Fallback to 1-second silent MP3
        log.debug("Falling back to silent MP3 for '{}'", fileName);
        String base64Mp3 = "SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA/+M4wAAAAAAAAAAAAEluZm8AAAAPAAAAAwAAAbAAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV////////////////////////////////////////////AAAAAExhdmM1OC4xMwAAAAAAAAAAAAAAACQDkAAAAAAAAAGw9wrNaQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/+MYxAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxDsAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxHYAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV";
        return java.util.Base64.getDecoder().decode(base64Mp3);
    }
}
