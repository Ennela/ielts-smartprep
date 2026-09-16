package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.HistoryFeedItemResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.HistoryFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryFeedController {

    private final HistoryFeedService historyFeedService;

    /**
     * The merged history of every skill for the current user, one page at a time.
     * GET /api/v1/history?skill=&from=&q=&page=&size=
     */
    @GetMapping
    @Operation(summary = "Get the user's merged history across Reading, Listening, Writing and mock tests",
            description = "Returns a Spring Data Page, newest first: the rows are under `content`, with "
                    + "`totalElements`, `totalPages`, `number` and `size` beside it. `size` is capped at 100. "
                    + "Each row carries `skill`, the id in that skill's own table (`refId`), the skill's own "
                    + "label (`title`), the band (`score`), and for mock tests the grading `status`.")
    public ResponseEntity<ApiResponse<Page<HistoryFeedItemResponse>>> feed(
            @AuthenticationPrincipal User user,
            @Parameter(description = "READING, LISTENING, WRITING or MOCK_TEST; omit for all")
            @RequestParam(required = false) String skill,
            @Parameter(description = "Only sittings submitted at or after this ISO date-time", example = "2026-08-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Case-insensitive substring of the row's title")
            @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page number", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)", example = "8") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                historyFeedService.feed(user.getUserId(), skill, from, q, page, size)));
    }
}
