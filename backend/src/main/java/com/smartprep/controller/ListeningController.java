package com.smartprep.controller;

import com.smartprep.dto.request.ListeningGenerateRequest;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.ListeningService;
import com.smartprep.service.StorageService;
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

    private final ListeningService listeningService;
    private final StorageService storageService;

    /**
     * Get all listening parts.
     * GET /api/v1/listening/parts
     */
    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<List<ListeningPartResponse>>> getAllParts() {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.getAllParts()));
    }

    /**
     * Get a specific part with questions (no answers).
     * GET /api/v1/listening/parts/{partId}
     */
    @GetMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<ListeningPartResponse>> getPartById(@PathVariable Long partId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.getPartById(partId)));
    }

    /**
     * Assemble a random mock test (4 parts, avoid recent).
     * GET /api/v1/listening/mock-test
     */
    @GetMapping("/mock-test")
    public ResponseEntity<ApiResponse<List<ListeningPartResponse>>> assembleMockTest(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.assembleMockTest(user.getUserId())));
    }

    /**
     * Submit test answers for grading.
     * POST /api/v1/listening/submit
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ListeningTestResponse>> submitTest(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ListeningSubmitRequest request) {
        ListeningTestResponse result = listeningService.submitTest(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(result, "Test graded successfully"));
    }

    /**
     * Get listening test history.
     * GET /api/v1/listening/history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ListeningHistoryResponse>>> getHistory(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.getHistory(user.getUserId())));
    }

    /**
     * AI analysis for a specific question.
     * POST /api/v1/listening/ai-analyze/{questionId}
     */
    @PostMapping("/ai-analyze/{questionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeQuestion(
            @PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.analyzeQuestion(questionId)));
    }

    /**
     * AI vocabulary extraction from a part's transcript.
     * POST /api/v1/listening/vocabulary/{partId}
     */
    @PostMapping("/vocabulary/{partId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> extractVocabulary(
            @PathVariable Long partId) {
        return ResponseEntity.ok(ApiResponse.ok(listeningService.extractVocabulary(partId)));
    }

    /**
     * Generate a new listening part using AI.
     * POST /api/v1/listening/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ListeningPartResponse>> generatePart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ListeningGenerateRequest request) {
        ListeningPartResponse response = listeningService.generatePart(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Manually trigger audio generation for a listening part.
     * POST /api/v1/listening/{partId}/generate-audio
     */
    @PostMapping("/{partId}/generate-audio")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateAudio(@PathVariable Long partId) {
        listeningService.generateAudio(partId);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(
                        Map.of("status", "PENDING", "message", "Audio generation started"),
                        "Audio generation queued"));
    }

    /**
     * Serve listening audio files.
     * First tries MinIO (real TTS audio), then falls back to silent MP3.
     * GET /api/v1/listening/audio/{fileName}
     */
    @GetMapping(value = "/audio/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> getAudioFile(@PathVariable String fileName) {
        // Try MinIO first (real TTS audio)
        try {
            byte[] audioBytes = storageService.downloadAudio(fileName);
            org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(audioBytes);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"))
                    .body(resource);
        } catch (Exception e) {
            log.debug("Audio not found in MinIO for '{}', trying classpath", fileName);
        }

        // Try classpath static resource
        try {
            org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource("static/api/v1/listening/audio/" + fileName);
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"))
                        .body(resource);
            }
        } catch (Exception e) {
            // Fall through to silence fallback
        }

        // Fallback to 1-second silent MP3 (only when TTS fails)
        log.debug("Falling back to silent MP3 for '{}'", fileName);
        String base64Mp3 = "SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA/+M4wAAAAAAAAAAAAEluZm8AAAAPAAAAAwAAAbAAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV////////////////////////////////////////////AAAAAExhdmM1OC4xMwAAAAAAAAAAAAAAACQDkAAAAAAAAAGw9wrNaQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/+MYxAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxDsAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxHYAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV";
        byte[] audioBytes = java.util.Base64.getDecoder().decode(base64Mp3);
        org.springframework.core.io.ByteArrayResource byteArrayResource =
            new org.springframework.core.io.ByteArrayResource(audioBytes);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"))
                .body(byteArrayResource);
    }
}
