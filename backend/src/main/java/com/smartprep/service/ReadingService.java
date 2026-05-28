package com.smartprep.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.dto.response.ReadingHistoryResponse;
import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.dto.response.ReadingResultResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.ReadingPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingService {

    private final ReadingQuizRepository quizRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ReadingPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    // Band score conversion table: index = number of correct answers
    private static final BigDecimal[] BAND_SCORES = {
            new BigDecimal("2.0"),  // 0/5
            new BigDecimal("3.5"),  // 1/5
            new BigDecimal("5.0"),  // 2/5
            new BigDecimal("6.0"),  // 3/5
            new BigDecimal("7.5"),  // 4/5
            new BigDecimal("9.0")   // 5/5
    };

    // Timer durations in seconds per difficulty
    private static final Map<Difficulty, Integer> TIME_LIMITS = Map.of(
            Difficulty.PASSAGE_1, 600,   // 10 minutes
            Difficulty.PASSAGE_2, 900,   // 15 minutes
            Difficulty.PASSAGE_3, 1200   // 20 minutes
    );

    /**
     * Generate a new reading quiz using Gemini AI.
     */
    @Transactional
    public ReadingQuizResponse generateQuiz(Long userId, ReadingGenerateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Topic topic = parseEnum(Topic.class, request.getTopic(), "Invalid topic");
        Difficulty difficulty = parseEnum(Difficulty.class, request.getDifficulty(), "Invalid difficulty");

        // Build AI prompts
        String systemPrompt = promptBuilder.buildSystemPrompt(difficulty);
        String userPrompt = promptBuilder.buildUserPrompt(topic, difficulty);

        // Call Gemini API
        String aiResponse = geminiClient.generate(systemPrompt, userPrompt);

        // Parse AI response
        ReadingQuiz quiz = parseAiResponse(aiResponse, user, topic, difficulty);

        // Save to database
        quiz = quizRepository.save(quiz);

        return mapToQuizResponse(quiz);
    }

    /**
     * Get a quiz by ID (hides answers if not yet submitted).
     */
    @Transactional(readOnly = true)
    public ReadingQuizResponse getQuiz(Long quizId, Long userId) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        return mapToQuizResponse(quiz);
    }

    /**
     * Submit answers, calculate score, and save results.
     */
    @Transactional
    public ReadingResultResponse submitQuiz(Long quizId, Long userId, ReadingSubmitRequest request) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));

        if (quiz.getSubmittedAt() != null) {
            throw new IllegalArgumentException("Quiz has already been submitted");
        }

        Map<Long, String> answers = request.getAnswers();
        int correctCount = 0;

        for (ReadingQuestion question : quiz.getQuestions()) {
            String userAnswer = answers.getOrDefault(question.getQuestionId(), "");
            question.setUserAnswer(userAnswer);

            if (isCorrect(question, userAnswer)) {
                correctCount++;
            }
        }

        // Calculate band score
        BigDecimal bandScore = calculateBandScore(correctCount);

        quiz.setCorrectAnswers(correctCount);
        quiz.setScore(bandScore);
        quiz.setSubmittedAt(LocalDateTime.now());
        quizRepository.save(quiz);

        // Save to score history
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.READING)
                .score(bandScore)
                .build();
        scoreHistoryRepository.save(history);

        return mapToResultResponse(quiz);
    }

    /**
     * Get quiz history for a user.
     */
    @Transactional(readOnly = true)
    public List<ReadingHistoryResponse> getHistory(Long userId) {
        return quizRepository.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(q -> q.getSubmittedAt() != null)
                .map(q -> ReadingHistoryResponse.builder()
                        .quizId(q.getQuizId())
                        .topic(q.getTopic().name())
                        .difficulty(q.getDifficulty().name())
                        .bandScore(q.getScore())
                        .correctAnswers(q.getCorrectAnswers())
                        .totalQuestions(q.getTotalQuestions())
                        .createdAt(q.getCreatedAt())
                        .submittedAt(q.getSubmittedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ReadingQuiz parseAiResponse(String aiResponse, User user, Topic topic, Difficulty difficulty) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);

            String passage = root.path("passage").asText();
            if (passage == null || passage.isBlank()) {
                throw new InvalidAiResponseException("AI response missing 'passage' field");
            }

            JsonNode questionsNode = root.path("questions");
            if (!questionsNode.isArray() || questionsNode.isEmpty()) {
                throw new InvalidAiResponseException("AI response missing 'questions' array");
            }

            ReadingQuiz quiz = ReadingQuiz.builder()
                    .user(user)
                    .topic(topic)
                    .difficulty(difficulty)
                    .passageText(passage)
                    .timeLimitSeconds(TIME_LIMITS.get(difficulty))
                    .totalQuestions(questionsNode.size())
                    .build();

            List<ReadingQuestion> questions = new ArrayList<>();
            for (int i = 0; i < questionsNode.size(); i++) {
                JsonNode qNode = questionsNode.get(i);
                ReadingQuestion question = ReadingQuestion.builder()
                        .quiz(quiz)
                        .questionType(parseQuestionType(qNode.path("type").asText()))
                        .questionText(qNode.path("questionText").asText())
                        .correctAnswer(qNode.path("correctAnswer").asText())
                        .explanation(qNode.path("explanation").asText())
                        .orderIndex(i + 1)
                        .build();

                // Parse MCQ options
                JsonNode optionsNode = qNode.path("options");
                if (optionsNode.isArray() && !optionsNode.isEmpty()) {
                    if (optionsNode.size() > 0) question.setOptionA(optionsNode.get(0).asText());
                    if (optionsNode.size() > 1) question.setOptionB(optionsNode.get(1).asText());
                    if (optionsNode.size() > 2) question.setOptionC(optionsNode.get(2).asText());
                    if (optionsNode.size() > 3) question.setOptionD(optionsNode.get(3).asText());
                }

                questions.add(question);
            }

            quiz.setQuestions(questions);
            return quiz;

        } catch (InvalidAiResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse AI response: {}", aiResponse, ex);
            throw new InvalidAiResponseException("Failed to parse AI response into quiz format", ex);
        }
    }

    private QuestionType parseQuestionType(String type) {
        if ("MCQ".equalsIgnoreCase(type)) return QuestionType.MCQ;
        if ("TFNG".equalsIgnoreCase(type)) return QuestionType.TFNG;
        return QuestionType.MCQ; // default fallback
    }

    private boolean isCorrect(ReadingQuestion question, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String correct = question.getCorrectAnswer().trim();
        String answer = userAnswer.trim();

        if (question.getQuestionType() == QuestionType.TFNG) {
            // Case-insensitive for TFNG
            return correct.equalsIgnoreCase(answer);
        }
        // For MCQ, compare the option letter (A, B, C, D)
        return correct.equalsIgnoreCase(answer);
    }

    private BigDecimal calculateBandScore(int correctAnswers) {
        int idx = Math.min(Math.max(correctAnswers, 0), BAND_SCORES.length - 1);
        return BAND_SCORES[idx];
    }

    private ReadingQuizResponse mapToQuizResponse(ReadingQuiz quiz) {
        List<ReadingQuizResponse.QuestionDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> ReadingQuizResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .orderIndex(q.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        return ReadingQuizResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .passageText(quiz.getPassageText())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .submitted(quiz.getSubmittedAt() != null)
                .createdAt(quiz.getCreatedAt())
                .questions(questionDtos)
                .build();
    }

    private ReadingResultResponse mapToResultResponse(ReadingQuiz quiz) {
        List<ReadingResultResponse.QuestionResultDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> ReadingResultResponse.QuestionResultDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .orderIndex(q.getOrderIndex())
                        .correctAnswer(q.getCorrectAnswer())
                        .userAnswer(q.getUserAnswer())
                        .correct(isCorrect(q, q.getUserAnswer()))
                        .explanation(q.getExplanation())
                        .build())
                .collect(Collectors.toList());

        return ReadingResultResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .passageText(quiz.getPassageText())
                .correctAnswers(quiz.getCorrectAnswers())
                .totalQuestions(quiz.getTotalQuestions())
                .bandScore(quiz.getScore())
                .questions(questionDtos)
                .build();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String errorMsg) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMsg + ": " + value);
        }
    }
}
