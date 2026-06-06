package com.smartprep.service;

import com.smartprep.dto.response.HistoryDetailResponse;
import com.smartprep.dto.response.UserAnswerResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.UserAnswer;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserAnswerRepository;
import com.smartprep.service.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final GeminiClient geminiClient;

    /**
     * Get detailed history including all user answers for review.
     */
    @Transactional(readOnly = true)
    public HistoryDetailResponse getHistoryDetail(Long historyId, Long userId) {
        ScoreHistory history = scoreHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Score history not found"));

        // Verify ownership
        if (!history.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Score history not found");
        }

        List<UserAnswer> answers = userAnswerRepository
                .findByScoreHistoryHistoryIdOrderByQuestionNoAsc(historyId);

        int correctCount = (int) answers.stream().filter(UserAnswer::getIsCorrect).count();

        List<UserAnswerResponse> answerResponses = answers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return HistoryDetailResponse.builder()
                .historyId(history.getHistoryId())
                .skillType(history.getSkillType().name())
                .score(history.getScore())
                .recordedAt(history.getRecordedAt())
                .totalQuestions(answers.size())
                .correctCount(correctCount)
                .answers(answerResponses)
                .build();
    }

    /**
     * Generate AI explanation for a specific answer using Gemini.
     * Caches the explanation in the database for subsequent requests.
     */
    @Transactional
    public UserAnswerResponse explainAnswer(Long historyId, Long answerId, Long userId) {
        ScoreHistory history = scoreHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResourceNotFoundException("Score history not found"));

        if (!history.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Score history not found");
        }

        UserAnswer answer = userAnswerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found"));

        if (!answer.getScoreHistory().getHistoryId().equals(historyId)) {
            throw new ResourceNotFoundException("Answer does not belong to this history");
        }

        // If explanation already cached, return it
        if (answer.getExplanation() != null && !answer.getExplanation().isBlank()) {
            return mapToResponse(answer);
        }

        // Generate explanation via Gemini AI
        String systemPrompt = """
                You are an expert IELTS tutor. Explain why the correct answer is right and, 
                if applicable, why the student's answer is wrong. Be concise (2-4 sentences), 
                supportive, and educational. Focus on the key reasoning or evidence from typical 
                IELTS question patterns. Respond in plain text, not JSON.
                """;

        String userPrompt = String.format(
                "Question (%s): %s\nStudent's answer: %s\nCorrect answer: %s\n\n" +
                "Explain why the correct answer is '%s' and why '%s' is %s.",
                answer.getQuestionType(),
                answer.getQuestionText(),
                answer.getUserAnswer() != null ? answer.getUserAnswer() : "(not answered)",
                answer.getCorrectAnswer(),
                answer.getCorrectAnswer(),
                answer.getUserAnswer() != null ? answer.getUserAnswer() : "(not answered)",
                answer.getIsCorrect() ? "also correct" : "incorrect"
        );

        try {
            String explanation = geminiClient.generate(systemPrompt, userPrompt);
            answer.setExplanation(explanation);
            userAnswerRepository.save(answer);
        } catch (Exception e) {
            log.warn("Failed to generate AI explanation for answer {}: {}", answerId, e.getMessage());
            answer.setExplanation("Unable to generate explanation at this time. Please try again later.");
        }

        return mapToResponse(answer);
    }

    private UserAnswerResponse mapToResponse(UserAnswer answer) {
        return UserAnswerResponse.builder()
                .answerId(answer.getAnswerId())
                .questionNo(answer.getQuestionNo())
                .questionText(answer.getQuestionText())
                .questionType(answer.getQuestionType())
                .userAnswer(answer.getUserAnswer())
                .correctAnswer(answer.getCorrectAnswer())
                .isCorrect(answer.getIsCorrect())
                .explanation(answer.getExplanation())
                .optionsJson(answer.getOptionsJson())
                .evidenceText(answer.getEvidenceText())
                .evidenceOffset(answer.getEvidenceOffset())
                .evidenceLength(answer.getEvidenceLength())
                .build();
    }
}
