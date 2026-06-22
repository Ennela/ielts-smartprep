package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.repository.ListeningQuestionRepository;
import com.smartprep.repository.ReadingQuestionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminQuestionController {

    private final ReadingQuestionRepository readingQuestionRepository;
    private final ListeningQuestionRepository listeningQuestionRepository;

    @Data
    public static class QuestionUpdateRequest {
        private String questionText;
        private String correctAnswer;
        private String explanation;
        private String evidenceText;
        private Integer evidenceOffset;
        private Integer evidenceLength;
    }

    @PostMapping("/{category}/{id}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyQuestion(
            @PathVariable String category,
            @PathVariable Long id,
            @RequestBody(required = false) QuestionUpdateRequest request) {
        if ("reading".equalsIgnoreCase(category)) {
            ReadingQuestion question = readingQuestionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Reading question not found with id: " + id));
            
            if (request != null) {
                if (request.getQuestionText() != null) question.setQuestionText(request.getQuestionText());
                if (request.getCorrectAnswer() != null) question.setCorrectAnswer(request.getCorrectAnswer());
                if (request.getExplanation() != null) question.setExplanation(request.getExplanation());
                if (request.getEvidenceText() != null) question.setEvidenceText(request.getEvidenceText());
                if (request.getEvidenceOffset() != null) question.setEvidenceOffset(request.getEvidenceOffset());
                if (request.getEvidenceLength() != null) question.setEvidenceLength(request.getEvidenceLength());
            }
            
            question.setVerified(true);
            readingQuestionRepository.save(question);
            return ResponseEntity.ok(ApiResponse.ok(null, "Reading question verified successfully"));
        } else if ("listening".equalsIgnoreCase(category)) {
            ListeningQuestion question = listeningQuestionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Listening question not found with id: " + id));
            
            if (request != null) {
                if (request.getQuestionText() != null) question.setQuestionText(request.getQuestionText());
                if (request.getCorrectAnswer() != null) question.setCorrectAnswer(request.getCorrectAnswer());
            }
            
            question.setVerified(true);
            listeningQuestionRepository.save(question);
            return ResponseEntity.ok(ApiResponse.ok(null, "Listening question verified successfully"));
        } else {
            throw new IllegalArgumentException("Invalid question category: " + category);
        }
    }
}
