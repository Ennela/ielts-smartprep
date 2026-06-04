package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.WritingGradeResponse;
import com.smartprep.dto.response.WritingHistoryResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.repository.WritingSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only queries for Writing submissions and history.
 * Extracted from the original WritingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
public class WritingQueryService {

    private final WritingSubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<WritingHistoryResponse> getHistory(Long userId) {
        return submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(s -> WritingHistoryResponse.builder()
                        .submissionId(s.getSubmissionId())
                        .promptId(s.getPrompt().getPromptId())
                        .essayType(s.getPrompt().getEssayType().name())
                        .promptTextPreview(truncate(s.getPrompt().getPromptText(), 100))
                        .overallBand(s.getOverallBand())
                        .wordCount(s.getWordCount())
                        .submittedAt(s.getSubmittedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WritingGradeResponse getSubmission(Long userId, Long submissionId) {
        WritingSubmission submission = submissionRepository
                .findBySubmissionIdAndUserUserId(submissionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        List<WritingGradeResponse.ErrorDto> errors;
        try {
            errors = objectMapper.readValue(
                    submission.getErrorListJson(),
                    new TypeReference<List<WritingGradeResponse.ErrorDto>>() {});
        } catch (Exception e) {
            errors = new ArrayList<>();
        }

        return buildGradeResponse(submission, submission.getPrompt(), errors, null);
    }

    public WritingGradeResponse buildGradeResponse(
            WritingSubmission submission, WritingPrompt prompt,
            List<WritingGradeResponse.ErrorDto> errors, List<String> improvementNotes) {
        return WritingGradeResponse.builder()
                .submissionId(submission.getSubmissionId())
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
                .essayText(submission.getEssayText())
                .wordCount(submission.getWordCount())
                .overallBand(submission.getOverallBand())
                .taskResponse(submission.getTaskResponseScore())
                .coherence(submission.getCoherenceScore())
                .lexical(submission.getLexicalScore())
                .grammar(submission.getGrammarScore())
                .errors(errors)
                .generalFeedback(submission.getAiFeedback())
                .rewrittenVersion(submission.getRewrittenVersion())
                .improvementNotes(improvementNotes)
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
