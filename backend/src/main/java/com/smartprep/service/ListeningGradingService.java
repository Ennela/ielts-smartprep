package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.ListeningTestResponse;
import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.TestMode;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scoring, submission, and score-history persistence for Listening tests.
 * Extracted from the original ListeningService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningGradingService {

    private final ListeningPartRepository partRepository;
    private final ListeningTestRepository testRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ListeningQueryService listeningQueryService;

    // IELTS Listening band score map (out of 40 questions)
    private static final Map<Integer, BigDecimal> BAND_SCORE_MAP = new LinkedHashMap<>();
    static {
        BAND_SCORE_MAP.put(40, new BigDecimal("9.0")); BAND_SCORE_MAP.put(39, new BigDecimal("9.0"));
        BAND_SCORE_MAP.put(38, new BigDecimal("8.5")); BAND_SCORE_MAP.put(37, new BigDecimal("8.5"));
        BAND_SCORE_MAP.put(36, new BigDecimal("8.0")); BAND_SCORE_MAP.put(35, new BigDecimal("8.0"));
        BAND_SCORE_MAP.put(34, new BigDecimal("7.5")); BAND_SCORE_MAP.put(33, new BigDecimal("7.5"));
        BAND_SCORE_MAP.put(32, new BigDecimal("7.5")); BAND_SCORE_MAP.put(31, new BigDecimal("7.0"));
        BAND_SCORE_MAP.put(30, new BigDecimal("7.0")); BAND_SCORE_MAP.put(29, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(28, new BigDecimal("6.5")); BAND_SCORE_MAP.put(27, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(26, new BigDecimal("6.5")); BAND_SCORE_MAP.put(25, new BigDecimal("6.0"));
        BAND_SCORE_MAP.put(24, new BigDecimal("6.0")); BAND_SCORE_MAP.put(23, new BigDecimal("6.0"));
        BAND_SCORE_MAP.put(22, new BigDecimal("5.5")); BAND_SCORE_MAP.put(21, new BigDecimal("5.5"));
        BAND_SCORE_MAP.put(20, new BigDecimal("5.5")); BAND_SCORE_MAP.put(19, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(18, new BigDecimal("5.0")); BAND_SCORE_MAP.put(17, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(16, new BigDecimal("5.0")); BAND_SCORE_MAP.put(15, new BigDecimal("4.5"));
        BAND_SCORE_MAP.put(14, new BigDecimal("4.5")); BAND_SCORE_MAP.put(13, new BigDecimal("4.5"));
        BAND_SCORE_MAP.put(12, new BigDecimal("4.0")); BAND_SCORE_MAP.put(11, new BigDecimal("4.0"));
        BAND_SCORE_MAP.put(10, new BigDecimal("4.0")); BAND_SCORE_MAP.put(9, new BigDecimal("3.5"));
        BAND_SCORE_MAP.put(8, new BigDecimal("3.5"));  BAND_SCORE_MAP.put(7, new BigDecimal("3.0"));
        BAND_SCORE_MAP.put(6, new BigDecimal("3.0"));  BAND_SCORE_MAP.put(5, new BigDecimal("2.5"));
        BAND_SCORE_MAP.put(4, new BigDecimal("2.5"));  BAND_SCORE_MAP.put(3, new BigDecimal("2.0"));
        BAND_SCORE_MAP.put(2, new BigDecimal("2.0"));  BAND_SCORE_MAP.put(1, new BigDecimal("1.0"));
        BAND_SCORE_MAP.put(0, new BigDecimal("0.0"));
    }

    @Transactional
    public ListeningTestResponse submitTest(Long userId, ListeningSubmitRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TestMode mode = TestMode.valueOf(request.getTestMode().toUpperCase());

        List<ListeningPart> parts = request.getPartIds().stream()
                .map(id -> partRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + id)))
                .collect(Collectors.toList());

        int totalQuestions = 0, correctCount = 0;
        List<ListeningTestResponse.PartResult> partResults = new ArrayList<>();

        for (ListeningPart part : parts) {
            List<ListeningTestResponse.QuestionResult> questionResults = new ArrayList<>();
            for (ListeningQuestion q : part.getQuestions()) {
                totalQuestions++;
                String userAnswer = request.getAnswers().getOrDefault(q.getQuestionId(), "");
                boolean isCorrect = checkAnswer(q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                if (isCorrect) correctCount++;

                questionResults.add(ListeningTestResponse.QuestionResult.builder()
                        .questionId(q.getQuestionId()).questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(listeningQueryService.mapOptions(q.getOptions(), true))
                        .correctAnswer(q.getCorrectAnswer()).userAnswer(userAnswer)
                        .isCorrect(isCorrect).orderIndex(q.getOrderIndex()).build());
            }

            partResults.add(ListeningTestResponse.PartResult.builder()
                    .partId(part.getPartId()).partNumber(part.getPartNumber())
                    .title(part.getTitle()).topic(part.getTopic())
                    .audioUrl(part.getAudioUrl()).transcriptText(part.getTranscriptText())
                    .questions(questionResults).build());
        }

        BigDecimal bandScore = calculateBandScore(correctCount, totalQuestions);

        // Save test
        ListeningTest test = ListeningTest.builder()
                .user(user).testMode(mode).score(bandScore)
                .totalQuestions(totalQuestions).correctAnswers(correctCount).build();

        List<ListeningTestPart> testParts = new ArrayList<>();
        for (ListeningPart part : parts) {
            Map<Long, String> partAnswers = new HashMap<>();
            for (ListeningQuestion q : part.getQuestions()) {
                partAnswers.put(q.getQuestionId(), request.getAnswers().getOrDefault(q.getQuestionId(), ""));
            }
            String answersJson;
            try { answersJson = objectMapper.writeValueAsString(partAnswers); }
            catch (Exception e) { answersJson = "{}"; }

            testParts.add(ListeningTestPart.builder().test(test).part(part).userAnswersJson(answersJson).build());
        }
        test.setTestParts(testParts);
        test = testRepository.save(test);

        String difficultyStr = parts.size() == 1 ? "PART_" + parts.get(0).getPartNumber() : "FULL_TEST";

        // Save score history with user answers
        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.LISTENING).score(bandScore).difficulty(difficultyStr).build();
        List<UserAnswer> userAnswerList = new ArrayList<>();
        int questionNo = 0;
        for (ListeningPart part : parts) {
            for (ListeningQuestion q : part.getQuestions()) {
                questionNo++;
                String userAns = request.getAnswers().getOrDefault(q.getQuestionId(), "");
                boolean correct = checkAnswer(q.getCorrectAnswer(), userAns, q.getQuestionType().name());
                String optSnapshot = null;
                if (q.getOptions() != null && !q.getOptions().isEmpty()) {
                    try {
                        optSnapshot = objectMapper.writeValueAsString(
                                q.getOptions().stream()
                                        .map(o -> Map.of("label", o.getLabel(), "content", o.getContent()))
                                        .collect(Collectors.toList()));
                    } catch (Exception e) { log.warn("Failed to serialize options: {}", e.getMessage()); }
                }
                userAnswerList.add(UserAnswer.builder()
                        .scoreHistory(history).questionNo(questionNo).questionText(q.getQuestionText())
                        .questionType(q.getQuestionType().name()).userAnswer(userAns)
                        .correctAnswer(q.getCorrectAnswer()).isCorrect(correct).optionsJson(optSnapshot).build());
            }
        }
        history.setUserAnswers(userAnswerList);
        scoreHistoryRepository.save(history);

        return ListeningTestResponse.builder()
                .testId(test.getTestId()).testMode(mode.name()).score(bandScore)
                .totalQuestions(totalQuestions).correctAnswers(correctCount)
                .submittedAt(test.getSubmittedAt()).parts(partResults).build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean checkAnswer(String correct, String userAnswer, String questionType) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String cleanCorrect = correct.trim().toLowerCase();
        String cleanUser = userAnswer.trim().toLowerCase();
        if ("MCQ".equals(questionType)) return cleanCorrect.equals(cleanUser);
        if (cleanCorrect.equals(cleanUser)) return true;
        return normalizeSpelling(cleanCorrect).equals(normalizeSpelling(cleanUser));
    }

    private String normalizeSpelling(String s) {
        return s.replace("organisation", "organization").replace("centre", "center")
                .replace("colour", "color").replace("favour", "favor")
                .replace("behaviour", "behavior").replace("travelling", "traveling")
                .replace("cancelled", "canceled")
                .replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }

    private BigDecimal calculateBandScore(int correct, int total) {
        if (total == 0) return BigDecimal.ZERO;
        if (total == 40) return BAND_SCORE_MAP.getOrDefault(Math.min(correct, 40), BigDecimal.ZERO);
        double scaled = ((double) correct / total) * 40.0;
        int scaledInt = (int) Math.round(scaled);
        return BAND_SCORE_MAP.getOrDefault(Math.min(scaledInt, 40), BigDecimal.ZERO);
    }
}
