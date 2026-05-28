package com.smartprep.controller;

import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.model.entity.User;
import com.smartprep.service.ListeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/listening")
@RequiredArgsConstructor
public class ListeningController {

    private final ListeningService listeningService;

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
     * Serve listening audio files. Falls back to a 1-second silent MP3 if physical file does not exist.
     * GET /api/v1/listening/audio/{fileName}
     */
    @GetMapping(value = "/audio/{fileName}", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getAudioFile(@PathVariable String fileName) {
        try {
            org.springframework.core.io.ClassPathResource resource = 
                new org.springframework.core.io.ClassPathResource("static/api/v1/listening/audio/" + fileName);
            if (resource.exists()) {
                try (java.io.InputStream is = resource.getInputStream()) {
                    byte[] fileBytes = is.readAllBytes();
                    return ResponseEntity.ok()
                            .header("Content-Type", "audio/mpeg")
                            .header("Accept-Ranges", "bytes")
                            .body(fileBytes);
                }
            }
        } catch (Exception e) {
            // Fall through to silence fallback
        }

        // Fallback to 1-second silent MP3
        String base64Mp3 = "SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA/+M4wAAAAAAAAAAAAEluZm8AAAAPAAAAAwAAAbAAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV////////////////////////////////////////////AAAAAExhdmM1OC4xMwAAAAAAAAAAAAAAACQDkAAAAAAAAAGw9wrNaQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/+MYxAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxDsAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxHYAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV";
        byte[] audioBytes = java.util.Base64.getDecoder().decode(base64Mp3);
        return ResponseEntity.ok()
                .header("Content-Type", "audio/mpeg")
                .header("Accept-Ranges", "bytes")
                .body(audioBytes);
    }
}
