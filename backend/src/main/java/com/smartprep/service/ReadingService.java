package com.smartprep.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.dto.request.ReadingSubmitFullRequest;
import com.smartprep.dto.response.*;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
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
    private final org.springframework.cache.CacheManager cacheManager;
    private final AdaptiveService adaptiveService;

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
        Difficulty difficulty;
        String focusQuestionType = null;

        if ("ADAPTIVE".equalsIgnoreCase(request.getDifficulty())) {
            AdaptiveService.AdaptiveConfig adaptiveConfig = adaptiveService.suggestNextConfig(userId, SkillType.READING);
            difficulty = Difficulty.valueOf(adaptiveConfig.nextDifficulty);
            focusQuestionType = adaptiveConfig.focusQuestionType;
            log.info("Adaptive Reading config: difficulty={}, focusQuestion={}", difficulty, focusQuestionType);
        } else {
            difficulty = parseEnum(Difficulty.class, request.getDifficulty(), "Invalid difficulty");
        }

        // Build AI prompts
        String systemPrompt = promptBuilder.buildSystemPrompt(difficulty);
        String userPrompt = promptBuilder.buildUserPrompt(topic, difficulty, focusQuestionType);

        // Cache lookup/populating
        String cacheKey = topic.name() + "-" + difficulty.name() + (focusQuestionType != null ? "-" + focusQuestionType : "");
        org.springframework.cache.Cache cache = cacheManager.getCache("readingAiResponse");
        String cachedResponse = null;
        if (cache != null) {
            cachedResponse = cache.get(cacheKey, String.class);
        }

        final String aiResponseText;
        if (cachedResponse != null) {
            aiResponseText = cachedResponse;
            log.info("Cached reading quiz content found in Redis for key: {}", cacheKey);
        } else {
            aiResponseText = geminiClient.generate(systemPrompt, userPrompt);
            if (cache != null && aiResponseText != null) {
                cache.put(cacheKey, aiResponseText);
            }
        }

        // Call parser and validate
        ReadingQuiz quiz;
        try {
            quiz = parseAiResponse(aiResponseText, user, topic, difficulty);
            validateReadingQuiz(quiz);
        } catch (Exception e) {
            // If parsing fails for cached value, evict cache and try generating once more
            if (cache != null && cachedResponse != null) {
                cache.evict(cacheKey);
            }
            log.warn("Parsing of AI response failed. Retrying fresh generation.", e);
            String freshResponse = geminiClient.generate(systemPrompt, userPrompt);
            quiz = parseAiResponse(freshResponse, user, topic, difficulty);
            validateReadingQuiz(quiz);
            if (cache != null && freshResponse != null) {
                cache.put(cacheKey, freshResponse);
            }
        }

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

        return startTemplateQuizInternal(user, template);
    }

    private ReadingQuizResponse startTemplateQuizInternal(User user, ReadingQuiz template) {
        ReadingQuiz userQuiz = ReadingQuiz.builder()
                .user(user)
                .topic(template.getTopic())
                .difficulty(template.getDifficulty())
                .passageText(template.getPassageText())
                .timeLimitSeconds(template.getTimeLimitSeconds())
                .totalQuestions(template.getTotalQuestions())
                .isTemplate(false)
                .parentTemplateId(template.getQuizId())
                .build();

        List<ReadingQuestion> questions = template.getQuestions().stream()
                .map(q -> {
                    ReadingQuestion question = ReadingQuestion.builder()
                            .quiz(userQuiz)
                            .questionType(q.getQuestionType())
                            .questionText(q.getQuestionText())
                            .correctAnswer(q.getCorrectAnswer())
                            .explanation(q.getExplanation())
                            .orderIndex(q.getOrderIndex())
                            .optionsJson(q.getOptionsJson())
                            .wordLimit(q.getWordLimit())
                            .groupLabel(q.getGroupLabel())
                            .groupId(q.getGroupId())
                            .groupContext(q.getGroupContext())
                            .build();

                    List<QuestionOption> options = new ArrayList<>();
                    if (q.getOptions() != null) {
                        for (QuestionOption opt : q.getOptions()) {
                            options.add(QuestionOption.builder()
                                    .readingQuestion(question)
                                    .label(opt.getLabel())
                                    .content(opt.getContent())
                                    .isCorrect(opt.getIsCorrect())
                                    .orderIndex(opt.getOrderIndex())
                                    .build());
                        }
                    }
                    question.setOptions(options);
                    return question;
                })
                .collect(Collectors.toList());

        userQuiz.setQuestions(questions);
        ReadingQuiz saved = quizRepository.save(userQuiz);

        return mapToQuizResponse(saved);
    }

    /**
     * Assemble 3 Reading passages (Passage 1, Passage 2, Passage 3) for a full test.
     */
    @Transactional
    public List<ReadingQuizResponse> assembleMockTest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ReadingQuizResponse> quizzes = new ArrayList<>();
        Difficulty[] difficulties = {Difficulty.PASSAGE_1, Difficulty.PASSAGE_2, Difficulty.PASSAGE_3};

        for (Difficulty difficulty : difficulties) {
            // Try to find admin templates first
            org.springframework.data.domain.Page<ReadingQuiz> templatePage = quizRepository.findQuizzesForAdmin(
                    null, difficulty, "ADMIN", PageRequest.of(0, 100)
            );
            List<ReadingQuiz> candidates = new ArrayList<>(templatePage.getContent());

            // If no templates, fall back to AI generated templates/quizzes
            if (candidates.isEmpty()) {
                org.springframework.data.domain.Page<ReadingQuiz> aiPage = quizRepository.findQuizzesForAdmin(
                        null, difficulty, "AI", PageRequest.of(0, 100)
                );
                candidates.addAll(aiPage.getContent());
            }

            if (candidates.isEmpty()) {
                throw new ResourceNotFoundException("No reading quizzes available for difficulty " + difficulty.name() + ". Please generate one first.");
            }

            // Pick a random quiz and clone it for the user
            ReadingQuiz template = candidates.get(new java.util.Random().nextInt(candidates.size()));
            ReadingQuizResponse userQuiz = startTemplateQuizInternal(user, template);
            quizzes.add(userQuiz);
        }

        return quizzes;
    }

    /**
     * Submit full Reading test (3 passages), calculate overall band score out of 40.
     */
    @Transactional
    public ReadingFullResultResponse submitFullQuiz(Long userId, ReadingSubmitFullRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ReadingResultResponse> quizResults = new ArrayList<>();
        int totalCorrect = 0;
        int totalQuestions = 0;

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

                boolean correct = isCorrect(question, userAnswer);
                if (correct) {
                    quizCorrect++;
                    totalCorrect++;
                }

                allUserAnswers.add(buildUserAnswer(null, questionCounter, question, userAnswer, correct));
            }

            // Grade this quiz locally (out of 13) for individual presentation fallback
            BigDecimal quizBand = calculateBandScore(quizCorrect);
            quiz.setCorrectAnswers(quizCorrect);
            quiz.setScore(quizBand);
            quiz.setSubmittedAt(LocalDateTime.now());
            quizRepository.save(quiz);

            quizResults.add(mapToResultResponse(quiz));
        }

        // Calculate overall Reading band score from cumulative correct answers out of 40
        BigDecimal overallBand = com.smartprep.service.util.IeltsScoringUtils.calculateReadingBand(totalCorrect);

        // Save a single entry to score history representing the full Reading test attempt
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.READING)
                .score(overallBand)
                .difficulty("FULL_TEST")
                .build();
        // Attach user answers for cascade persist
        allUserAnswers.forEach(ua -> ua.setScoreHistory(history));
        history.setUserAnswers(allUserAnswers);
        scoreHistoryRepository.save(history);

        return ReadingFullResultResponse.builder()
                .overallBand(overallBand)
                .totalCorrect(totalCorrect)
                .totalQuestions(totalQuestions)
                .submittedAt(LocalDateTime.now())
                .quizResults(quizResults)
                .build();
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

        // Save to score history with user answers
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.READING)
                .score(bandScore)
                .difficulty(quiz.getDifficulty().name())
                .build();
        // Build and attach user answers
        List<UserAnswer> userAnswerList = new ArrayList<>();
        for (ReadingQuestion question : quiz.getQuestions()) {
            String ua = answers.getOrDefault(question.getQuestionId(), "");
            boolean correct = isCorrect(question, ua);
            userAnswerList.add(buildUserAnswer(history, question.getOrderIndex(), question, ua, correct));
        }
        history.setUserAnswers(userAnswerList);
        scoreHistoryRepository.save(history);

        return mapToResultResponse(quiz);
    }

    /**
     * Get quiz history for a user.
     */
    @Transactional(readOnly = true)
    public List<ReadingHistoryResponse> getHistory(Long userId) {
        // Pre-load score histories indexed by recordedAt for historyId lookup
        List<ScoreHistory> readingHistories = scoreHistoryRepository
                .findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(sh -> sh.getSkillType() == SkillType.READING)
                .collect(Collectors.toList());

        return quizRepository.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(q -> q.getSubmittedAt() != null)
                .map(q -> {
                    // Find matching ScoreHistory by closest recordedAt to submittedAt
                    Long historyId = readingHistories.stream()
                            .filter(sh -> sh.getScore().compareTo(q.getScore()) == 0)
                            .filter(sh -> !sh.getRecordedAt().isBefore(q.getSubmittedAt().minusSeconds(5)))
                            .filter(sh -> !sh.getRecordedAt().isAfter(q.getSubmittedAt().plusSeconds(5)))
                            .map(ScoreHistory::getHistoryId)
                            .findFirst()
                            .orElse(null);

                    return ReadingHistoryResponse.builder()
                            .quizId(q.getQuizId())
                            .historyId(historyId)
                            .topic(q.getTopic().name())
                            .difficulty(q.getDifficulty().name())
                            .bandScore(q.getScore())
                            .correctAnswers(q.getCorrectAnswers())
                            .totalQuestions(q.getTotalQuestions())
                            .createdAt(q.getCreatedAt())
                            .submittedAt(q.getSubmittedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Build a UserAnswer entity from a ReadingQuestion for persistence with ScoreHistory.
     */
    private UserAnswer buildUserAnswer(ScoreHistory history, int questionNo,
                                        ReadingQuestion question, String userAnswerText, boolean correct) {
        // Build options JSON snapshot for MCQ questions
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
                .scoreHistory(history)
                .questionNo(questionNo)
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType().name())
                .userAnswer(userAnswerText)
                .correctAnswer(question.getCorrectAnswer())
                .isCorrect(correct)
                .explanation(question.getExplanation())
                .optionsJson(optionsSnapshot)
                .build();
    }

    private void validateReadingQuiz(ReadingQuiz quiz) {
        if (quiz == null) {
            throw new InvalidAiResponseException("Reading quiz is null");
        }
        if (quiz.getPassageText() == null || quiz.getPassageText().isBlank()) {
            throw new InvalidAiResponseException("Reading passage text cannot be empty");
        }
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new InvalidAiResponseException("Reading quiz must contain at least one question");
        }
        for (ReadingQuestion q : quiz.getQuestions()) {
            if (q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                throw new InvalidAiResponseException("Question text cannot be empty");
            }
            if (q.getCorrectAnswer() == null || q.getCorrectAnswer().isBlank()) {
                throw new InvalidAiResponseException("Question correct answer cannot be empty");
            }
            if (q.getQuestionType() == QuestionType.MCQ) {
                if (q.getOptions() == null || q.getOptions().isEmpty()) {
                    throw new InvalidAiResponseException("MCQ question is missing options");
                }
            }
        }
    }

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

                    ReadingQuestion question = ReadingQuestion.builder()
                            .quiz(quiz)
                            .questionType(qType)
                            .questionText(qNode.path("questionText").asText(""))
                            .correctAnswer(normalizedAnswer)
                            .explanation(qNode.path("explanation").asText(""))
                            .orderIndex(orderIndex++)
                            .groupLabel(groupLabel)
                            .groupId(groupIdCounter)
                            .groupContext(groupContext)
                            .wordLimit(wordLimit)
                            .build();

                    List<QuestionOption> options = new ArrayList<>();
                    JsonNode qOptionsNode = qNode.path("options");
                    if (qOptionsNode.isArray() && !qOptionsNode.isEmpty()) {
                        if (qType == QuestionType.MCQ) {
                            int optIndex = 1;
                            for (JsonNode optNode : qOptionsNode) {
                                String label;
                                String content;
                                if (optNode.isObject()) {
                                    label = optNode.path("label").asText();
                                    content = optNode.path("content").asText();
                                } else {
                                    String optText = optNode.asText();
                                    label = optText.length() > 0 ? optText.substring(0, 1).toUpperCase() : "";
                                    content = stripOptionPrefix(optText);
                                }
                                options.add(QuestionOption.builder()
                                        .readingQuestion(question)
                                        .label(label)
                                        .content(content)
                                        .isCorrect(normalizedAnswer.equalsIgnoreCase(label))
                                        .orderIndex(optIndex++)
                                        .build());
                            }
                        } else {
                            question.setOptionsJson(qOptionsNode.toString());
                        }
                    } else if (groupOptionsJson != null) {
                        question.setOptionsJson(groupOptionsJson);
                    }

                    question.setOptions(options);
                    questions.add(question);
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

            ReadingQuestion question = ReadingQuestion.builder()
                    .quiz(quiz)
                    .questionType(qType)
                    .questionText(qNode.path("questionText").asText())
                    .correctAnswer(normalizedCorrectAnswer)
                    .explanation(qNode.path("explanation").asText())
                    .orderIndex(i + 1)
                    .build();

            List<QuestionOption> options = new ArrayList<>();
            JsonNode optionsNode = qNode.path("options");
            if (optionsNode.isArray() && !optionsNode.isEmpty()) {
                int optIndex = 1;
                for (JsonNode optNode : optionsNode) {
                    String label;
                    String content;
                    if (optNode.isObject()) {
                        label = optNode.path("label").asText();
                        content = optNode.path("content").asText();
                    } else {
                        String optText = optNode.asText();
                        label = optText.length() > 0 ? optText.substring(0, 1).toUpperCase() : "";
                        content = stripOptionPrefix(optText);
                    }
                    options.add(QuestionOption.builder()
                            .readingQuestion(question)
                            .label(label)
                            .content(content)
                            .isCorrect(normalizedCorrectAnswer.equalsIgnoreCase(label))
                            .orderIndex(optIndex++)
                            .build());
                }
            }

            question.setOptions(options);
            questions.add(question);
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

            case SENTENCE_COMPLETION, SUMMARY_COMPLETION, FILL_BLANK -> {
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
            case SENTENCE_COMPLETION, SUMMARY_COMPLETION, FILL_BLANK -> {
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
                        .options(mapOptions(q.getOptions(), false))
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
                        .options(mapOptions(q.getOptions(), true))
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

    private List<QuestionOptionResponse> mapOptions(List<QuestionOption> options, boolean showCorrect) {
        if (options == null) return null;
        return options.stream()
                .map(o -> QuestionOptionResponse.builder()
                        .optionId(o.getOptionId())
                        .label(o.getLabel())
                        .content(o.getContent())
                        .isCorrect(showCorrect ? o.getIsCorrect() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String errorMsg) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMsg + ": " + value);
        }
    }
}
