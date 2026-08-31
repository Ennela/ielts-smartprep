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
import com.smartprep.service.util.IeltsScoringUtils;
import com.smartprep.service.util.QuestionOptionMapper;
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
    private final ExamAttemptService examAttemptService;


    @Transactional
    public ListeningTestResponse submitTest(Long userId, ListeningSubmitRequest request) {
        // Unlike Reading, this method creates a new ListeningTest on every call rather than
        // marking an existing one submitted, so there is no submittedAt flag to guard on.
        // The attempt is the only idempotency key available: without this check a repeated
        // submit writes a second ListeningTest and a second ScoreHistory row, duplicating
        // the user's history and progress. Submissions sent without an attemptId remain
        // unguarded — see B-63.
        if (request.getAttemptId() != null
                && examAttemptService.isAlreadySubmitted(request.getAttemptId(), userId)) {
            throw new IllegalArgumentException("This listening test has already been submitted");
        }

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
                boolean isCorrect = IeltsScoringUtils.isListeningCorrect(q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                if (isCorrect) correctCount++;

                questionResults.add(ListeningTestResponse.QuestionResult.builder()
                        .questionId(q.getQuestionId()).questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(QuestionOptionMapper.mapForReview(q.getOptions()))
                        .correctAnswer(q.getCorrectAnswer()).userAnswer(userAnswer)
                        .isCorrect(isCorrect).orderIndex(q.getOrderIndex()).build());
            }

            partResults.add(ListeningTestResponse.PartResult.builder()
                    .partId(part.getPartId()).partNumber(part.getPartNumber())
                    .title(part.getTitle()).topic(part.getTopic())
                    .audioUrl(part.getAudioUrl()).transcriptText(part.getTranscriptText())
                    .questions(questionResults).build());
        }

        // Shared 40-question scale: a 10-question part and a full paper now report the
        // same band for the same accuracy.
        BigDecimal bandScore = IeltsScoringUtils.calculateListeningBand(correctCount, totalQuestions);

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

        // Complete exam attempt if provided
        ExamAttempt completedAttempt = null;
        boolean autoSubmitted = request.getAutoSubmitted() != null && request.getAutoSubmitted();
        if (request.getAttemptId() != null) {
            completedAttempt = examAttemptService.completeAttemptInternal(
                    request.getAttemptId(), userId, autoSubmitted, null, null);
        }

        Integer timeSpentSeconds = completedAttempt != null ? completedAttempt.getTimeSpentSeconds() : null;

        // Save score history with user answers
        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.LISTENING).score(bandScore)
                .difficulty(difficultyStr)
                .timeSpentSeconds(timeSpentSeconds)
                .autoSubmitted(autoSubmitted)
                .build();
        List<UserAnswer> userAnswerList = new ArrayList<>();
        int questionNo = 0;
        for (ListeningPart part : parts) {
            for (ListeningQuestion q : part.getQuestions()) {
                questionNo++;
                String userAns = request.getAnswers().getOrDefault(q.getQuestionId(), "");
                boolean correct = IeltsScoringUtils.isListeningCorrect(q.getCorrectAnswer(), userAns, q.getQuestionType().name());
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
                .submittedAt(test.getSubmittedAt()).parts(partResults)
                .timeSpentSeconds(timeSpentSeconds)
                .autoSubmitted(autoSubmitted)
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================


}
