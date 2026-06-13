package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingSubmitFullRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.dto.response.ReadingFullResultResponse;
import com.smartprep.dto.response.ReadingResultResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scoring, submission, and score-history persistence for Reading quizzes.
 * Extracted from the original ReadingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingGradingService {

    private final ReadingQuizRepository quizRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ReadingQueryService readingQueryService;
    private final ExamAttemptService examAttemptService;

    // Band score conversion for 13-question quizzes
    private static final BigDecimal[] BAND_SCORES_13 = {
            new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("2.5"),
            new BigDecimal("3.0"), new BigDecimal("3.5"), new BigDecimal("4.0"),
            new BigDecimal("4.5"), new BigDecimal("5.0"), new BigDecimal("5.5"),
            new BigDecimal("6.0"), new BigDecimal("6.5"), new BigDecimal("7.5"),
            new BigDecimal("8.5"), new BigDecimal("9.0")
    };

    @Transactional
    public ReadingResultResponse submitQuiz(Long quizId, Long userId, ReadingSubmitRequest request) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (quiz.getSubmittedAt() != null) {
            throw new IllegalArgumentException("Quiz has already been submitted");
        }

        Map<Long, String> answers = request.getAnswers();
        int correctCount = 0;

        log.debug("Submitted answers map: {}", answers);
        for (ReadingQuestion question : quiz.getQuestions()) {
            String userAnswer = answers.getOrDefault(question.getQuestionId(), "");
            question.setUserAnswer(userAnswer);
            boolean correct = IeltsScoringUtils.isReadingCorrect(question.getQuestionType(), question.getCorrectAnswer(), userAnswer);
            log.debug("Q#{} ({}): correctAnswer='{}', userAnswer='{}', match={}",
                    question.getQuestionId(), question.getQuestionType(),
                    question.getCorrectAnswer(), userAnswer, correct);
            if (correct) correctCount++;
        }

        BigDecimal bandScore = calculateBandScore(correctCount);
        quiz.setCorrectAnswers(correctCount);
        quiz.setScore(bandScore);
        quiz.setSubmittedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.READING).score(bandScore)
                .difficulty(quiz.getDifficulty().name())
                .moduleType(quiz.getModuleType() != null ? quiz.getModuleType() : "ACADEMIC")
                .build();
        List<UserAnswer> userAnswerList = new ArrayList<>();
        for (ReadingQuestion question : quiz.getQuestions()) {
            String ua = answers.getOrDefault(question.getQuestionId(), "");
            boolean correct = IeltsScoringUtils.isReadingCorrect(question.getQuestionType(), question.getCorrectAnswer(), ua);
            userAnswerList.add(buildUserAnswer(history, question.getOrderIndex(), question, ua, correct));
        }
        history.setUserAnswers(userAnswerList);
        scoreHistoryRepository.save(history);

        return readingQueryService.mapToResultResponse(quiz);
    }

    @Transactional
    public ReadingFullResultResponse submitFullQuiz(Long userId, ReadingSubmitFullRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ReadingResultResponse> quizResults = new ArrayList<>();
        int totalCorrect = 0, totalQuestions = 0;
        List<UserAnswer> allUserAnswers = new ArrayList<>();
        int questionCounter = 0;

        for (Long quizId : request.getQuizIds()) {
            ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with ID: " + quizId));
            Map<Long, String> answers = request.getAnswers();
            int quizCorrect = 0;

            for (ReadingQuestion question : quiz.getQuestions()) {
                totalQuestions++;
                questionCounter++;
                String userAnswer = answers.getOrDefault(question.getQuestionId(), "");
                question.setUserAnswer(userAnswer);
                boolean correct = IeltsScoringUtils.isReadingCorrect(question.getQuestionType(), question.getCorrectAnswer(), userAnswer);
                if (correct) { quizCorrect++; totalCorrect++; }
                allUserAnswers.add(buildUserAnswer(null, questionCounter, question, userAnswer, correct));
            }

            BigDecimal quizBand = calculateBandScore(quizCorrect);
            quiz.setCorrectAnswers(quizCorrect);
            quiz.setScore(quizBand);
            quiz.setSubmittedAt(LocalDateTime.now());
            quizRepository.save(quiz);
            quizResults.add(readingQueryService.mapToResultResponse(quiz));
        }

        String moduleType = "ACADEMIC";
        if (!request.getQuizIds().isEmpty()) {
            ReadingQuiz firstQuiz = quizRepository.findById(request.getQuizIds().get(0)).orElse(null);
            if (firstQuiz != null && firstQuiz.getModuleType() != null) {
                moduleType = firstQuiz.getModuleType();
            }
        }

        BigDecimal overallBand = IeltsScoringUtils.calculateReadingBand(totalCorrect, moduleType);

        // Complete exam attempt if provided
        ExamAttempt completedAttempt = null;
        boolean autoSubmitted = request.getAutoSubmitted() != null && request.getAutoSubmitted();
        if (request.getAttemptId() != null) {
            completedAttempt = examAttemptService.completeAttemptInternal(
                    request.getAttemptId(), userId, autoSubmitted, null, null);
        }

        Integer timeSpentSeconds = completedAttempt != null ? completedAttempt.getTimeSpentSeconds() : null;

        ScoreHistory history = ScoreHistory.builder()
                .user(user).skillType(SkillType.READING).score(overallBand)
                .difficulty("FULL_TEST")
                .moduleType(moduleType)
                .timeSpentSeconds(timeSpentSeconds)
                .autoSubmitted(autoSubmitted)
                .build();
        allUserAnswers.forEach(ua -> ua.setScoreHistory(history));
        history.setUserAnswers(allUserAnswers);
        scoreHistoryRepository.save(history);

        return ReadingFullResultResponse.builder()
                .overallBand(overallBand).totalCorrect(totalCorrect)
                .totalQuestions(totalQuestions).submittedAt(LocalDateTime.now())
                .quizResults(quizResults)
                .timeSpentSeconds(timeSpentSeconds)
                .autoSubmitted(autoSubmitted)
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private BigDecimal calculateBandScore(int correctAnswers) {
        int idx = Math.min(Math.max(correctAnswers, 0), BAND_SCORES_13.length - 1);
        return BAND_SCORES_13[idx];
    }

    private UserAnswer buildUserAnswer(ScoreHistory history, int questionNo,
                                        ReadingQuestion question, String userAnswerText, boolean correct) {
        String optionsSnapshot = null;
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            try {
                optionsSnapshot = objectMapper.writeValueAsString(
                        question.getOptions().stream()
                                .map(o -> Map.of("label", o.getLabel(), "content", o.getContent()))
                                .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to serialize options for question {}: {}", question.getQuestionId(), e.getMessage());
            }
        } else if (question.getOptionsJson() != null) {
            optionsSnapshot = question.getOptionsJson();
        }

        return UserAnswer.builder()
                .scoreHistory(history).questionNo(questionNo)
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType().name())
                .userAnswer(userAnswerText).correctAnswer(question.getCorrectAnswer())
                .isCorrect(correct).explanation(question.getExplanation())
                .optionsJson(optionsSnapshot)
                .evidenceText(question.getEvidenceText())
                .evidenceOffset(question.getEvidenceOffset())
                .evidenceLength(question.getEvidenceLength())
                .build();
    }
}
