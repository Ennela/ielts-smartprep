package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.AdaptiveService;
import com.smartprep.service.ReadingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles AI-based reading quiz generation: prompt building, Gemini calls,
 * response parsing, validation, and persistence.
 * Extracted from the original ReadingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingGenerationService {

    public static final String PROMPT_VERSION = "v1.0";

    private final ReadingQuizRepository quizRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ReadingPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final AdaptiveService adaptiveService;
    private final ReadingQueryService readingQueryService;

    private static final Map<Difficulty, Integer> TIME_LIMITS = Map.of(
            Difficulty.PASSAGE_1, 900,
            Difficulty.PASSAGE_2, 1200,
            Difficulty.PASSAGE_3, 1500
    );

    @Transactional
    public ReadingQuizResponse generateQuiz(Long userId, ReadingGenerateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Topic topic = ReadingQueryService.parseEnum(Topic.class, request.getTopic(), "Invalid topic");
        int passageCount = request.getPassageCount() != null ? request.getPassageCount() : 1;

        if (passageCount == 3) {
            List<ReadingQuiz> quizzes = new ArrayList<>();
            quizzes.add(generateSingleQuizEntity(user, topic, Difficulty.PASSAGE_1, null));
            quizzes.add(generateSingleQuizEntity(user, topic, Difficulty.PASSAGE_2, null));
            quizzes.add(generateSingleQuizEntity(user, topic, Difficulty.PASSAGE_3, null));

            List<Long> quizIds = quizzes.stream()
                    .map(ReadingQuiz::getQuizId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            ReadingQuizResponse response = readingQueryService.mapToQuizResponse(quizzes.get(0));
            response.setQuizIds(quizIds);
            return response;
        } else {
            Difficulty difficulty;
            String focusQuestionType = null;

            if ("ADAPTIVE".equalsIgnoreCase(request.getDifficulty())) {
                AdaptiveService.AdaptiveConfig adaptiveConfig = adaptiveService.suggestNextConfig(userId, SkillType.READING);
                difficulty = Difficulty.valueOf(adaptiveConfig.nextDifficulty);
                focusQuestionType = adaptiveConfig.focusQuestionType;
                log.info("Adaptive Reading config: difficulty={}, focusQuestion={}", difficulty, focusQuestionType);
            } else {
                difficulty = ReadingQueryService.parseEnum(Difficulty.class, request.getDifficulty(), "Invalid difficulty");
            }

            ReadingQuiz quiz = generateSingleQuizEntity(user, topic, difficulty, focusQuestionType);
            ReadingQuizResponse response = readingQueryService.mapToQuizResponse(quiz);
            List<Long> quizIds = new ArrayList<>();
            if (quiz.getQuizId() != null) {
                quizIds.add(quiz.getQuizId());
            }
            response.setQuizIds(quizIds);
            return response;
        }
    }

    private ReadingQuiz generateSingleQuizEntity(User user, Topic topic, Difficulty difficulty, String focusQuestionType) {
        String systemPrompt = promptBuilder.buildSystemPrompt(difficulty);
        String userPrompt = promptBuilder.buildUserPrompt(topic, difficulty, focusQuestionType);

        String skillType = "READING";
        String questionTypesKey = focusQuestionType != null ? focusQuestionType : "ALL";
        String cacheKey = String.format("%s:%s:%s:%s:%s", skillType, topic.name(), difficulty.name(), questionTypesKey, PROMPT_VERSION);

        String cachedResponse = null;
        if (redisTemplate != null) {
            try {
                cachedResponse = redisTemplate.opsForValue().get(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to retrieve cache from Redis: {}", e.getMessage());
            }
        }

        String aiResponseText = null;
        boolean fromCache = false;

        if (cachedResponse != null) {
            aiResponseText = cachedResponse;
            fromCache = true;
            log.info("Cached reading quiz content found in Redis for key: {}", cacheKey);
        } else {
            try {
                aiResponseText = geminiClient.generate(systemPrompt, userPrompt);
                if (redisTemplate != null && aiResponseText != null) {
                    try {
                        redisTemplate.opsForValue().set(cacheKey, aiResponseText, java.time.Duration.ofHours(24));
                    } catch (Exception e) {
                        log.warn("Failed to write to Redis cache: {}", e.getMessage());
                    }
                }
            } catch (Exception ex) {
                return handleFallbackQuiz(user, topic, difficulty, ex);
            }
        }

        ReadingQuiz quiz;
        try {
            quiz = parseAiResponse(aiResponseText, user, topic, difficulty);
            validateReadingQuiz(quiz);
        } catch (Exception e) {
            log.warn("Parsing of AI response failed. Retrying fresh generation.", e);
            if (fromCache && redisTemplate != null) {
                try {
                    redisTemplate.delete(cacheKey);
                } catch (Exception dEx) {
                    log.warn("Failed to delete Redis cache key: {}", dEx.getMessage());
                }
            }
            try {
                String freshResponse = geminiClient.generate(systemPrompt, userPrompt);
                quiz = parseAiResponse(freshResponse, user, topic, difficulty);
                validateReadingQuiz(quiz);
                if (redisTemplate != null && freshResponse != null) {
                    try {
                        redisTemplate.opsForValue().set(cacheKey, freshResponse, java.time.Duration.ofHours(24));
                    } catch (Exception wEx) {
                        log.warn("Failed to write to Redis cache: {}", wEx.getMessage());
                    }
                }
            } catch (Exception ex) {
                return handleFallbackQuiz(user, topic, difficulty, ex);
            }
        }

        return quizRepository.save(quiz);
    }

    private ReadingQuiz handleFallbackQuiz(User user, Topic topic, Difficulty difficulty, Exception originalException) {
        log.warn("Gemini generation failed or quota exceeded. Falling back to pre-generated content for topic: {} and difficulty: {}", topic, difficulty);

        List<ReadingQuiz> templates = quizRepository.findQuizzesForAdmin(topic, difficulty, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();

        if (templates.isEmpty()) {
            templates = quizRepository.findQuizzesForAdmin(null, difficulty, null, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        }

        if (templates.isEmpty()) {
            templates = quizRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        }

        if (templates.isEmpty()) {
            log.error("No fallback quizzes found in the database. Failing request.");
            if (originalException instanceof RuntimeException) {
                throw (RuntimeException) originalException;
            }
            throw new RuntimeException("AI Content generation failed and no fallback content was available in the system.", originalException);
        }

        ReadingQuiz selected = templates.get(new java.util.Random().nextInt(templates.size()));
        log.info("Selected fallback ReadingQuiz ID: {} for user: {}", selected.getQuizId(), user.getUserId());

        ReadingQuiz fallbackQuiz = ReadingQuiz.builder()
                .user(user)
                .topic(selected.getTopic())
                .difficulty(selected.getDifficulty())
                .passageText(selected.getPassageText())
                .timeLimitSeconds(selected.getTimeLimitSeconds() != null ? selected.getTimeLimitSeconds() : TIME_LIMITS.get(selected.getDifficulty()))
                .totalQuestions(selected.getTotalQuestions())
                .isTemplate(false)
                .parentTemplateId(selected.getQuizId())
                .build();

        List<ReadingQuestion> clonedQuestions = new ArrayList<>();
        for (ReadingQuestion q : selected.getQuestions()) {
            ReadingQuestion clonedQ = ReadingQuestion.builder()
                    .quiz(fallbackQuiz)
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .correctAnswer(q.getCorrectAnswer())
                    .explanation(q.getExplanation())
                    .orderIndex(q.getOrderIndex())
                    .groupLabel(q.getGroupLabel())
                    .groupId(q.getGroupId())
                    .groupContext(q.getGroupContext())
                    .wordLimit(q.getWordLimit())
                    .optionsJson(q.getOptionsJson())
                    .evidenceText(q.getEvidenceText())
                    .evidenceOffset(q.getEvidenceOffset())
                    .evidenceLength(q.getEvidenceLength())
                    .build();

            if (q.getOptions() != null) {
                List<QuestionOption> clonedOptions = new ArrayList<>();
                for (QuestionOption opt : q.getOptions()) {
                    clonedOptions.add(QuestionOption.builder()
                            .readingQuestion(clonedQ)
                            .label(opt.getLabel())
                            .content(opt.getContent())
                            .isCorrect(opt.getIsCorrect())
                            .orderIndex(opt.getOrderIndex())
                            .build());
                }
                clonedQ.setOptions(clonedOptions);
            }
            clonedQuestions.add(clonedQ);
        }
        fallbackQuiz.setQuestions(clonedQuestions);

        return quizRepository.save(fallbackQuiz);
    }

    // =========================================================================
    // AI Response Parsing
    // =========================================================================

    private ReadingQuiz parseAiResponse(String aiResponse, User user, Topic topic, Difficulty difficulty) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);
            String passage = root.path("passage").asText();
            if (passage == null || passage.isBlank()) {
                throw new InvalidAiResponseException("AI response missing 'passage' field");
            }

            PassageEvidenceParser.CleanedPassageResult parsedPassage = PassageEvidenceParser.parse(passage);

            JsonNode groupsNode = root.path("questionGroups");
            if (!groupsNode.isArray() || groupsNode.isEmpty()) {
                return parseLegacyFormat(root, user, topic, difficulty, passage);
            }

            ReadingQuiz quiz = ReadingQuiz.builder()
                    .user(user).topic(topic).difficulty(difficulty)
                    .passageText(parsedPassage.cleanedPassage).timeLimitSeconds(TIME_LIMITS.get(difficulty))
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

                String groupOptionsJson = null;
                JsonNode groupOptionsNode = groupNode.path("options");
                if (groupOptionsNode.isArray() && !groupOptionsNode.isEmpty()) {
                    groupOptionsJson = groupOptionsNode.toString();
                }

                QuestionType qType = parseQuestionType(groupType);
                JsonNode questionsNode = groupNode.path("questions");
                if (!questionsNode.isArray()) continue;

                for (JsonNode qNode : questionsNode) {
                    int currentQNum = orderIndex++;
                    String rawCorrectAnswer = qNode.path("correctAnswer").asText("").trim();
                    String normalizedAnswer = normalizeCorrectAnswer(rawCorrectAnswer, qType);

                    // Extract evidence text and position
                    String evidenceText = null;
                    Integer evidenceOffset = null;
                    Integer evidenceLength = null;
                    for (PassageEvidenceParser.EvidenceInfo ev : parsedPassage.evidences) {
                        if (ev.questionNo == currentQNum) {
                            evidenceText = ev.text;
                            evidenceOffset = ev.offset;
                            evidenceLength = ev.length;
                            break;
                        }
                    }

                    // Strip any tags from explanations
                    String rawExplanation = qNode.path("explanation").asText("");
                    String cleanedExplanation = rawExplanation
                            .replaceAll("\\[ANS_\\d+\\]", "")
                            .replaceAll("\\[/ANS_\\d+\\]", "");

                    ReadingQuestion question = ReadingQuestion.builder()
                            .quiz(quiz).questionType(qType)
                            .questionText(qNode.path("questionText").asText(""))
                            .correctAnswer(normalizedAnswer)
                            .explanation(cleanedExplanation)
                            .orderIndex(currentQNum).groupLabel(groupLabel)
                            .groupId(groupIdCounter).groupContext(groupContext).wordLimit(wordLimit)
                            .evidenceText(evidenceText)
                            .evidenceOffset(evidenceOffset)
                            .evidenceLength(evidenceLength)
                            .build();

                    List<QuestionOption> options = new ArrayList<>();
                    JsonNode qOptionsNode = qNode.path("options");
                    if (qOptionsNode.isArray() && !qOptionsNode.isEmpty()) {
                        if (qType == QuestionType.MCQ) {
                            int optIndex = 1;
                            for (JsonNode optNode : qOptionsNode) {
                                String label, content;
                                if (optNode.isObject()) {
                                    label = optNode.path("label").asText();
                                    content = optNode.path("content").asText();
                                } else {
                                    String optText = optNode.asText();
                                    label = optText.length() > 0 ? optText.substring(0, 1).toUpperCase() : "";
                                    content = stripOptionPrefix(optText);
                                }
                                options.add(QuestionOption.builder()
                                        .readingQuestion(question).label(label).content(content)
                                        .isCorrect(normalizedAnswer.equalsIgnoreCase(label))
                                        .orderIndex(optIndex++).build());
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

    private ReadingQuiz parseLegacyFormat(JsonNode root, User user, Topic topic, Difficulty difficulty, String passage) {
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new InvalidAiResponseException("AI response missing both 'questionGroups' and 'questions'");
        }

        PassageEvidenceParser.CleanedPassageResult parsedPassage = PassageEvidenceParser.parse(passage);

        ReadingQuiz quiz = ReadingQuiz.builder()
                .user(user).topic(topic).difficulty(difficulty)
                .passageText(parsedPassage.cleanedPassage).timeLimitSeconds(TIME_LIMITS.get(difficulty))
                .totalQuestions(questionsNode.size())
                .build();

        List<ReadingQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionsNode.size(); i++) {
            JsonNode qNode = questionsNode.get(i);
            String rawCorrectAnswer = qNode.path("correctAnswer").asText("").trim();
            QuestionType qType = parseQuestionType(qNode.path("type").asText());
            String normalizedCorrectAnswer = normalizeCorrectAnswer(rawCorrectAnswer, qType);

            int currentQNum = i + 1;
            String evidenceText = null;
            Integer evidenceOffset = null;
            Integer evidenceLength = null;
            for (PassageEvidenceParser.EvidenceInfo ev : parsedPassage.evidences) {
                if (ev.questionNo == currentQNum) {
                    evidenceText = ev.text;
                    evidenceOffset = ev.offset;
                    evidenceLength = ev.length;
                    break;
                }
            }

            String rawExplanation = qNode.path("explanation").asText("");
            String cleanedExplanation = rawExplanation
                    .replaceAll("\\[ANS_\\d+\\]", "")
                    .replaceAll("\\[/ANS_\\d+\\]", "");

            ReadingQuestion question = ReadingQuestion.builder()
                    .quiz(quiz).questionType(qType)
                    .questionText(qNode.path("questionText").asText())
                    .correctAnswer(normalizedCorrectAnswer)
                    .explanation(cleanedExplanation)
                    .evidenceText(evidenceText)
                    .evidenceOffset(evidenceOffset)
                    .evidenceLength(evidenceLength)
                    .orderIndex(currentQNum).build();

            List<QuestionOption> options = new ArrayList<>();
            JsonNode optionsNode = qNode.path("options");
            if (optionsNode.isArray() && !optionsNode.isEmpty()) {
                int optIndex = 1;
                for (JsonNode optNode : optionsNode) {
                    String label, content;
                    if (optNode.isObject()) {
                        label = optNode.path("label").asText();
                        content = optNode.path("content").asText();
                    } else {
                        String optText = optNode.asText();
                        label = optText.length() > 0 ? optText.substring(0, 1).toUpperCase() : "";
                        content = stripOptionPrefix(optText);
                    }
                    options.add(QuestionOption.builder()
                            .readingQuestion(question).label(label).content(content)
                            .isCorrect(normalizedCorrectAnswer.equalsIgnoreCase(label))
                            .orderIndex(optIndex++).build());
                }
            }
            question.setOptions(options);
            questions.add(question);
        }

        quiz.setQuestions(questions);
        return quiz;
    }

    // =========================================================================
    // Validation & Parsing Helpers
    // =========================================================================

    private void validateReadingQuiz(ReadingQuiz quiz) {
        if (quiz == null) throw new InvalidAiResponseException("Reading quiz is null");
        if (quiz.getPassageText() == null || quiz.getPassageText().isBlank())
            throw new InvalidAiResponseException("Reading passage text cannot be empty");
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty())
            throw new InvalidAiResponseException("Reading quiz must contain at least one question");
        for (ReadingQuestion q : quiz.getQuestions()) {
            if (q.getQuestionText() == null || q.getQuestionText().isBlank())
                throw new InvalidAiResponseException("Question text cannot be empty");
            if (q.getCorrectAnswer() == null || q.getCorrectAnswer().isBlank())
                throw new InvalidAiResponseException("Question correct answer cannot be empty");
            if (q.getQuestionType() == QuestionType.MCQ && (q.getOptions() == null || q.getOptions().isEmpty()))
                throw new InvalidAiResponseException("MCQ question is missing options");
        }
    }

    private QuestionType parseQuestionType(String type) {
        if (type == null || type.isBlank()) return QuestionType.MCQ;
        try {
            return QuestionType.valueOf(type.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
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
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    char firstChar = Character.toUpperCase(trimmed.charAt(0));
                    if (firstChar >= 'A' && firstChar <= 'D') yield String.valueOf(firstChar);
                }
                yield trimmed;
            }
            case MATCHING_INFORMATION, MATCHING_FEATURES, MATCHING_SENTENCE_ENDINGS -> {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    char firstChar = Character.toUpperCase(trimmed.charAt(0));
                    if (firstChar >= 'A' && firstChar <= 'Z') yield String.valueOf(firstChar);
                }
                yield trimmed;
            }
            case MATCHING_HEADINGS -> raw.trim().toLowerCase();
            case SENTENCE_COMPLETION, SUMMARY_COMPLETION, FILL_BLANK -> raw.trim();
        };
    }

    private String stripOptionPrefix(String optionText) {
        if (optionText == null) return null;
        return optionText.replaceFirst("^\\s*[A-Da-d]\\s*[.):]+\\s*", "").trim();
    }

    // =========================================================================
    // Evidence Tag Parser Utility
    // =========================================================================
    public static class PassageEvidenceParser {
        public static class CleanedPassageResult {
            public String cleanedPassage;
            public List<EvidenceInfo> evidences = new java.util.ArrayList<>();
        }

        public static class EvidenceInfo {
            public int questionNo;
            public String text;
            public int offset;
            public int length;
        }

        public static CleanedPassageResult parse(String rawPassage) {
            CleanedPassageResult result = new CleanedPassageResult();
            StringBuilder sb = new StringBuilder();
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[ANS_(\\d+)\\](.*?)\\[/ANS_\\1\\]", java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(rawPassage);
            
            int lastEnd = 0;
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                int qNum = Integer.parseInt(matcher.group(1));
                String evidenceText = matcher.group(2);
                
                sb.append(rawPassage, lastEnd, start);
                int cleanOffset = sb.length();
                sb.append(evidenceText);
                int cleanLength = evidenceText.length();
                
                EvidenceInfo info = new EvidenceInfo();
                info.questionNo = qNum;
                info.text = evidenceText;
                info.offset = cleanOffset;
                info.length = cleanLength;
                result.evidences.add(info);
                
                lastEnd = end;
            }
            sb.append(rawPassage, lastEnd, rawPassage.length());
            
            result.cleanedPassage = sb.toString();
            
            // Clean any remaining tags just in case
            result.cleanedPassage = result.cleanedPassage
                    .replaceAll("\\[ANS_\\d+\\]", "")
                    .replaceAll("\\[/ANS_\\d+\\]", "");
                    
            return result;
        }
    }
}
