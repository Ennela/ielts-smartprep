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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // IELTS Academic Reading band score conversion: correct answers out of 13 -> band
    // Based on official IELTS scale, compressed from 40 to 13 questions
    private static final BigDecimal[] BAND_SCORES_13 = {
            new BigDecimal("1.0"),  //  0/13
            new BigDecimal("2.0"),  //  1/13
            new BigDecimal("2.5"),  //  2/13
            new BigDecimal("3.0"),  //  3/13
            new BigDecimal("3.5"),  //  4/13
            new BigDecimal("4.0"),  //  5/13
            new BigDecimal("4.5"),  //  6/13
            new BigDecimal("5.0"),  //  7/13
            new BigDecimal("5.5"),  //  8/13
            new BigDecimal("6.0"),  //  9/13
            new BigDecimal("6.5"),  // 10/13
            new BigDecimal("7.5"),  // 11/13
            new BigDecimal("8.5"),  // 12/13
            new BigDecimal("9.0")   // 13/13
    };

    // Timer durations per difficulty (increased for 13 questions)
    private static final Map<Difficulty, Integer> TIME_LIMITS = Map.of(
            Difficulty.PASSAGE_1, 900,   // 15 minutes
            Difficulty.PASSAGE_2, 1200,  // 20 minutes
            Difficulty.PASSAGE_3, 1500   // 25 minutes
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
     * Get a paginated list of admin-provided reading quizzes.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReadingQuizResponse> getTemplateList(String topicStr, String difficultyStr, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Topic topic = (topicStr != null && !topicStr.isBlank()) ? parseEnum(Topic.class, topicStr, "Invalid topic") : null;
        Difficulty difficulty = (difficultyStr != null && !difficultyStr.isBlank()) ? parseEnum(Difficulty.class, difficultyStr, "Invalid difficulty") : null;
        return quizRepository.findQuizzesForAdmin(topic, difficulty, "ADMIN", pageRequest)
                .map(this::mapToQuizResponse);
    }

    /**
     * Start a new test attempt based on an admin-provided reading template.
     */
    @Transactional
    public ReadingQuizResponse startTemplateQuiz(Long userId, Long templateId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ReadingQuiz template = quizRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Reading template not found: " + templateId));

        if (!template.getIsTemplate()) {
            throw new IllegalArgumentException("Target quiz is not a template");
        }

        ReadingQuiz userQuiz = ReadingQuiz.builder()
                .user(user)
                .topic(template.getTopic())
                .difficulty(template.getDifficulty())
                .passageText(template.getPassageText())
                .timeLimitSeconds(template.getTimeLimitSeconds())
                .totalQuestions(template.getTotalQuestions())
                .isTemplate(false)
                .parentTemplateId(templateId)
                .build();

        List<ReadingQuestion> questions = template.getQuestions().stream()
                .map(q -> ReadingQuestion.builder()
                        .quiz(userQuiz)
                        .questionType(q.getQuestionType())
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .correctAnswer(q.getCorrectAnswer())
                        .explanation(q.getExplanation())
                        .orderIndex(q.getOrderIndex())
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
                        .build())
                .collect(Collectors.toList());

        userQuiz.setQuestions(questions);
        ReadingQuiz saved = quizRepository.save(userQuiz);

        return mapToQuizResponse(saved);
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
     * Get result for a submitted quiz.
     */
    @Transactional(readOnly = true)
    public ReadingResultResponse getResult(Long quizId, Long userId) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (quiz.getSubmittedAt() == null) {
            throw new IllegalArgumentException("Quiz has not been submitted yet");
        }
        return mapToResultResponse(quiz);
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

        log.debug("Submitted answers map: {}", answers);

        for (ReadingQuestion question : quiz.getQuestions()) {
            String userAnswer = answers.getOrDefault(question.getQuestionId(), "");
            question.setUserAnswer(userAnswer);

            boolean correct = isCorrect(question, userAnswer);
            log.debug("Q#{} ({}): correctAnswer='{}', userAnswer='{}', match={}",
                    question.getQuestionId(), question.getQuestionType(),
                    question.getCorrectAnswer(), userAnswer, correct);

            if (correct) {
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

    /**
     * Parse AI response in the new questionGroups format.
     */
    private ReadingQuiz parseAiResponse(String aiResponse, User user, Topic topic, Difficulty difficulty) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);

            String passage = root.path("passage").asText();
            if (passage == null || passage.isBlank()) {
                throw new InvalidAiResponseException("AI response missing 'passage' field");
            }

            JsonNode groupsNode = root.path("questionGroups");
            if (!groupsNode.isArray() || groupsNode.isEmpty()) {
                // Fallback: try legacy "questions" format for backward compatibility
                return parseLegacyFormat(root, user, topic, difficulty, passage);
            }

            ReadingQuiz quiz = ReadingQuiz.builder()
                    .user(user)
                    .topic(topic)
                    .difficulty(difficulty)
                    .passageText(passage)
                    .timeLimitSeconds(TIME_LIMITS.get(difficulty))
                    .build();

            List<ReadingQuestion> questions = new ArrayList<>();
            int orderIndex = 1;
            int groupIdCounter = 1;

            for (JsonNode groupNode : groupsNode) {
                String groupLabel = groupNode.path("groupLabel").asText("");
                String groupType = groupNode.path("groupType").asText("MCQ");
                String groupContext = groupNode.has("groupContext") && !groupNode.path("groupContext").isNull()
                        ? groupNode.path("groupContext").asText() : null;
                Integer wordLimit = groupNode.has("wordLimit") && !groupNode.path("wordLimit").isNull()
                        ? groupNode.path("wordLimit").asInt() : null;

                // Group-level options (for matching types)
                String groupOptionsJson = null;
                JsonNode groupOptionsNode = groupNode.path("options");
                if (groupOptionsNode.isArray() && !groupOptionsNode.isEmpty()) {
                    groupOptionsJson = groupOptionsNode.toString();
                }

                QuestionType qType = parseQuestionType(groupType);

                JsonNode questionsNode = groupNode.path("questions");
                if (!questionsNode.isArray()) continue;

                for (JsonNode qNode : questionsNode) {
                    String rawCorrectAnswer = qNode.path("correctAnswer").asText("").trim();
                    String normalizedAnswer = normalizeCorrectAnswer(rawCorrectAnswer, qType);

                    ReadingQuestion.ReadingQuestionBuilder builder = ReadingQuestion.builder()
                            .quiz(quiz)
                            .questionType(qType)
                            .questionText(qNode.path("questionText").asText(""))
                            .correctAnswer(normalizedAnswer)
                            .explanation(qNode.path("explanation").asText(""))
                            .orderIndex(orderIndex++)
                            .groupLabel(groupLabel)
                            .groupId(groupIdCounter)
                            .groupContext(groupContext)
                            .wordLimit(wordLimit);

                    // Question-level options (for MCQ inline options)
                    JsonNode qOptionsNode = qNode.path("options");
                    if (qOptionsNode.isArray() && !qOptionsNode.isEmpty()) {
                        // MCQ: set optionA-D from question-level options
                        if (qType == QuestionType.MCQ) {
                            if (qOptionsNode.size() > 0) builder.optionA(stripOptionPrefix(qOptionsNode.get(0).asText()));
                            if (qOptionsNode.size() > 1) builder.optionB(stripOptionPrefix(qOptionsNode.get(1).asText()));
                            if (qOptionsNode.size() > 2) builder.optionC(stripOptionPrefix(qOptionsNode.get(2).asText()));
                            if (qOptionsNode.size() > 3) builder.optionD(stripOptionPrefix(qOptionsNode.get(3).asText()));
                        } else {
                            // For other types with question-level options, store as JSON
                            builder.optionsJson(qOptionsNode.toString());
                        }
                    } else if (groupOptionsJson != null) {
                        // Use group-level options
                        builder.optionsJson(groupOptionsJson);
                    }

                    questions.add(builder.build());
                }

                groupIdCounter++;
            }

            quiz.setTotalQuestions(questions.size());
            quiz.setQuestions(questions);
            return quiz;

        } catch (InvalidAiResponseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse AI response: {}", aiResponse, ex);
            throw new InvalidAiResponseException("Failed to parse AI response into quiz format", ex);
        }
    }

    /**
     * Fallback parser for the legacy flat "questions" array format.
     */
    private ReadingQuiz parseLegacyFormat(JsonNode root, User user, Topic topic, Difficulty difficulty, String passage) {
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new InvalidAiResponseException("AI response missing both 'questionGroups' and 'questions'");
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
            String rawCorrectAnswer = qNode.path("correctAnswer").asText("").trim();
            QuestionType qType = parseQuestionType(qNode.path("type").asText());
            String normalizedCorrectAnswer = normalizeCorrectAnswer(rawCorrectAnswer, qType);

            ReadingQuestion.ReadingQuestionBuilder builder = ReadingQuestion.builder()
                    .quiz(quiz)
                    .questionType(qType)
                    .questionText(qNode.path("questionText").asText())
                    .correctAnswer(normalizedCorrectAnswer)
                    .explanation(qNode.path("explanation").asText())
                    .orderIndex(i + 1);

            JsonNode optionsNode = qNode.path("options");
            if (optionsNode.isArray() && !optionsNode.isEmpty()) {
                if (optionsNode.size() > 0) builder.optionA(stripOptionPrefix(optionsNode.get(0).asText()));
                if (optionsNode.size() > 1) builder.optionB(stripOptionPrefix(optionsNode.get(1).asText()));
                if (optionsNode.size() > 2) builder.optionC(stripOptionPrefix(optionsNode.get(2).asText()));
                if (optionsNode.size() > 3) builder.optionD(stripOptionPrefix(optionsNode.get(3).asText()));
            }

            questions.add(builder.build());
        }

        quiz.setQuestions(questions);
        return quiz;
    }

    private QuestionType parseQuestionType(String type) {
        if (type == null || type.isBlank()) return QuestionType.MCQ;
        try {
            return QuestionType.valueOf(type.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            // Handle common aliases
            String upper = type.toUpperCase().trim();
            if (upper.contains("TFNG") || upper.contains("TRUE_FALSE")) return QuestionType.TFNG;
            if (upper.contains("YNNG") || upper.contains("YES_NO")) return QuestionType.YNNG;
            if (upper.contains("SENTENCE") && upper.contains("COMPLETION")) return QuestionType.SENTENCE_COMPLETION;
            if (upper.contains("SUMMARY") || upper.contains("NOTE")) return QuestionType.SUMMARY_COMPLETION;
            if (upper.contains("HEADING")) return QuestionType.MATCHING_HEADINGS;
            if (upper.contains("INFORMATION")) return QuestionType.MATCHING_INFORMATION;
            if (upper.contains("FEATURE")) return QuestionType.MATCHING_FEATURES;
            if (upper.contains("SENTENCE") && upper.contains("ENDING")) return QuestionType.MATCHING_SENTENCE_ENDINGS;
            log.warn("Unknown question type '{}', defaulting to MCQ", type);
            return QuestionType.MCQ;
        }
    }

    /**
     * Check if the user's answer is correct for a given question type.
     */
    private boolean isCorrect(ReadingQuestion question, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String correct = question.getCorrectAnswer().trim();
        String answer = userAnswer.trim();

        return switch (question.getQuestionType()) {
            case TFNG, YNNG ->
                    // Case-insensitive exact match for TRUE/FALSE/NOT GIVEN and YES/NO/NOT GIVEN
                    correct.equalsIgnoreCase(answer);

            case MCQ, MATCHING_INFORMATION, MATCHING_FEATURES, MATCHING_SENTENCE_ENDINGS ->
                    // Case-insensitive letter/numeral match
                    correct.equalsIgnoreCase(answer);

            case MATCHING_HEADINGS ->
                    // Roman numeral comparison (case-insensitive)
                    correct.equalsIgnoreCase(answer);

            case SENTENCE_COMPLETION, SUMMARY_COMPLETION -> {
                // Flexible text matching: case-insensitive, strip articles and extra spaces
                String normCorrect = normalizeCompletionText(correct);
                String normAnswer = normalizeCompletionText(answer);
                yield normCorrect.equals(normAnswer);
            }
        };
    }

    /**
     * Normalize text for completion-type comparison.
     * Strips leading articles (a, an, the), collapses whitespace, lowercases.
     */
    private String normalizeCompletionText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("^(the|a|an)\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalize the correctAnswer from AI response.
     */
    private String normalizeCorrectAnswer(String raw, QuestionType type) {
        if (raw == null || raw.isBlank()) return raw;

        return switch (type) {
            case TFNG -> {
                String upper = raw.toUpperCase().trim();
                if (upper.startsWith("NOT")) yield "NOT GIVEN";
                if (upper.startsWith("TRUE") || upper.equals("T")) yield "TRUE";
                if (upper.startsWith("FALSE") || upper.equals("F")) yield "FALSE";
                yield upper;
            }
            case YNNG -> {
                String upper = raw.toUpperCase().trim();
                if (upper.startsWith("NOT")) yield "NOT GIVEN";
                if (upper.startsWith("YES") || upper.equals("Y")) yield "YES";
                if (upper.startsWith("NO") && !upper.startsWith("NOT")) yield "NO";
                yield upper;
            }
            case MCQ -> {
                // Extract the first letter (A, B, C, or D)
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    char firstChar = Character.toUpperCase(trimmed.charAt(0));
                    if (firstChar >= 'A' && firstChar <= 'D') {
                        yield String.valueOf(firstChar);
                    }
                }
                yield trimmed;
            }
            case MATCHING_INFORMATION, MATCHING_FEATURES, MATCHING_SENTENCE_ENDINGS -> {
                // Extract letter (A-Z)
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    char firstChar = Character.toUpperCase(trimmed.charAt(0));
                    if (firstChar >= 'A' && firstChar <= 'Z') {
                        yield String.valueOf(firstChar);
                    }
                }
                yield trimmed;
            }
            case MATCHING_HEADINGS -> {
                // Roman numeral — keep as-is but lowercase
                yield raw.trim().toLowerCase();
            }
            case SENTENCE_COMPLETION, SUMMARY_COMPLETION -> {
                // Keep original text, just trim
                yield raw.trim();
            }
        };
    }

    /**
     * Strip common letter prefix from AI option text.
     */
    private String stripOptionPrefix(String optionText) {
        if (optionText == null) return null;
        return optionText.replaceFirst("^\\s*[A-Da-d]\\s*[.):]+\\s*", "").trim();
    }

    private BigDecimal calculateBandScore(int correctAnswers) {
        int idx = Math.min(Math.max(correctAnswers, 0), BAND_SCORES_13.length - 1);
        return BAND_SCORES_13[idx];
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
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
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
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
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
