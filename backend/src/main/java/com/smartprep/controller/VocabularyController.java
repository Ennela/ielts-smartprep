package com.smartprep.controller;

import com.smartprep.dto.request.VocabBulkSaveRequest;
import com.smartprep.dto.request.VocabCreateRequest;
import com.smartprep.dto.request.VocabReviewRequest;
import com.smartprep.dto.request.VocabAiSuggestRequest;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.VocabResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.vocab.VocabAiService;
import com.smartprep.service.vocab.VocabularyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vocab")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @PostMapping
    public ResponseEntity<ApiResponse<VocabResponse>> addVocabulary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody VocabCreateRequest request) {
        VocabResponse response = vocabularyService.addVocabulary(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/due")
    public ResponseEntity<ApiResponse<Page<VocabResponse>>> getDueVocabulary(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dueDate").ascending());
        Page<VocabResponse> response = vocabularyService.getDueVocabularies(user.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVocabStats(
            @AuthenticationPrincipal User user) {
        Map<String, Object> response = vocabularyService.getVocabStats(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VocabResponse>>> getAllVocabulary(
            @AuthenticationPrincipal User user) {
        List<VocabResponse> response = vocabularyService.getAllVocabularies(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<VocabResponse>> reviewVocabulary(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody VocabReviewRequest request) {
        VocabResponse response = vocabularyService.reviewVocabulary(user.getUserId(), id, request.getGrade());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/ai-suggest")
    public ResponseEntity<ApiResponse<List<VocabAiService.SuggestedVocab>>> aiSuggestVocabulary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody VocabAiSuggestRequest request) {
        List<VocabAiService.SuggestedVocab> response = vocabularyService.suggestVocabulary(
                user.getUserId(), request.getSkillType(), request.getSourceId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/bulk-save")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkSaveVocabulary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody VocabBulkSaveRequest request) {
        int count = vocabularyService.bulkSaveVocabulary(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "Successfully saved vocabulary items.",
                "savedCount", count
        )));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteVocabulary(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        vocabularyService.deleteVocabulary(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Vocabulary item deleted successfully")));
    }
}
