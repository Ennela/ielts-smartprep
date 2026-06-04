package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.AdaptiveService;
import com.smartprep.service.ListeningQueryService;
import com.smartprep.service.TtsService;
import com.smartprep.service.AudioGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI-based Listening part generation, analysis, and vocabulary extraction.
 * Extracted from the original ListeningService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningGenerationService {

    private final ListeningPartRepository partRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ListeningPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final org.springframework.cache.CacheManager cacheManager;
    private final AdaptiveService adaptiveService;
    private final TtsService ttsService;
    private final AudioGenerationService audioGenerationService;
    private final ListeningQueryService listeningQueryService;

    // ========== AI Generation ==========

    @Transactional
    public ListeningPartResponse generatePart(Long userId, com.smartprep.dto.request.ListeningGenerateRequest request) {
        int partNumber;
        String focusQuestionType = null;
        if (Boolean.TRUE.equals(request.getAdaptive())) {
            AdaptiveService.AdaptiveConfig config = adaptiveService.suggestNextConfig(userId, SkillType.LISTENING);
            partNumber = Integer.parseInt(config.nextDifficulty);
            focusQuestionType = config.focusQuestionType;
            log.info("Adaptive Listening config: partNumber={}, focusQuestion={}", partNumber, focusQuestionType);
        } else {
            if (request.getPartNumber() == null) {
                throw new IllegalArgumentException("Part number is required when not in adaptive mode");
            }
            partNumber = request.getPartNumber();
        }
        return generatePartInternal(userId, partNumber, request.getTopic(), focusQuestionType);
    }

    @Transactional
    public ListeningPartResponse generatePart(Long userId, int partNumber, String topic) {
        return generatePartInternal(userId, partNumber, topic, null);
    }

    private ListeningPartResponse generatePartInternal(Long userId, int partNumber, String topic, String focusQuestionType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String systemPrompt = "You are a professional IELTS Listening test designer with 15 years of experience.";
        String userPrompt = promptBuilder.buildGeneratePrompt(partNumber, topic, focusQuestionType);

        String cacheKey = partNumber + "-" + (topic != null ? topic.toUpperCase() : "GENERAL")
                + (focusQuestionType != null ? "-" + focusQuestionType : "");
        org.springframework.cache.Cache cache = cacheManager.getCache("listeningAiResponse");
        String cachedResponse = null;
        if (cache != null) {
            cachedResponse = cache.get(cacheKey, String.class);
        }

        final String aiResponseText;
        if (cachedResponse != null) {
            aiResponseText = cachedResponse;
            log.info("Cached listening part content found in Redis for key: {}", cacheKey);
        } else {
            aiResponseText = geminiClient.generate(systemPrompt, userPrompt);
            if (cache != null && aiResponseText != null) {
                cache.put(cacheKey, aiResponseText);
            }
        }

        ListeningPart part;
        try {
            part = parseListeningPart(aiResponseText, partNumber, topic);
            validateListeningPart(part);
        } catch (Exception e) {
            if (cache != null && cachedResponse != null) cache.evict(cacheKey);
            log.warn("Parsing of AI response failed. Retrying fresh generation.", e);
            String freshResponse = geminiClient.generate(systemPrompt, userPrompt);
            part = parseListeningPart(freshResponse, partNumber, topic);
            validateListeningPart(part);
            if (cache != null && freshResponse != null) cache.put(cacheKey, freshResponse);
        }

        ListeningPart saved = partRepository.save(part);
        if (ttsService.isAvailable()) {
            audioGenerationService.generateAudioAsync(saved.getPartId());
        } else {
            log.info("TTS unavailable, part {} will use silent fallback", saved.getPartId());
        }
        return listeningQueryService.toPartResponse(saved);
    }

    // ========== AI Post-Analysis ==========

    @Transactional(readOnly = true)
    public Map<String, Object> analyzeQuestion(Long questionId) {
        ListeningPart part = partRepository.findAll().stream()
                .filter(p -> p.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals(questionId)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        ListeningQuestion question = part.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        String prompt = String.format(
                "Analyze this IELTS Listening question. The correct answer is '%s'.\n\n"
                + "Transcript:\n%s\n\nQuestion: %s\n\n"
                + "Provide analysis in JSON format:\n"
                + "{\n  \"correctAnswerLocation\": \"<exact quote from transcript containing the answer>\",\n"
                + "  \"trapExplanation\": \"<explain any distractors or traps in the audio>\",\n"
                + "  \"paraphraseAnalysis\": \"<explain any paraphrasing between question and transcript>\",\n"
                + "  \"tip\": \"<practical tip to avoid this type of mistake>\"\n}\n\nReturn ONLY valid JSON.",
                question.getCorrectAnswer(), part.getTranscriptText(), question.getQuestionText());

        String response = geminiClient.generate(AI_ANALYZE_SYSTEM, prompt);
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            throw new InvalidAiResponseException("Failed to parse AI analysis: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> extractVocabulary(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found"));

        String prompt = String.format(
                "Scan this IELTS listening transcript and extract advanced vocabulary "
                + "(B2 to C2 level CEFR). Return JSON:\n"
                + "{\n  \"vocabularies\": [\n    {\n"
                + "      \"word\": \"<word or phrase>\",\n      \"partOfSpeech\": \"<noun/verb/adj/adv>\",\n"
                + "      \"vietnameseMeaning\": \"<Vietnamese translation>\",\n"
                + "      \"contextExample\": \"<sentence from transcript containing the word>\",\n"
                + "      \"level\": \"<B2/C1/C2>\"\n    }\n  ]\n}\n\nTranscript:\n%s\n\nReturn ONLY valid JSON. Extract at least 8 words.",
                part.getTranscriptText());

        String response = geminiClient.generate(AI_VOCAB_SYSTEM, prompt);
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            throw new InvalidAiResponseException("Failed to parse vocabulary response: " + e.getMessage());
        }
    }

    // =========================================================================
    // Parsing & Validation
    // =========================================================================

    private ListeningPart parseListeningPart(String aiResponse, int partNumber, String topic) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);
            String title = root.path("title").asText("AI Generated Part " + partNumber);
            String chosenTopic = root.path("topic").asText(topic != null ? topic : "General");
            String script = root.path("script").asText();
            if (script == null || script.isBlank()) {
                throw new InvalidAiResponseException("AI response missing 'script' field");
            }

            ListeningPart part = ListeningPart.builder()
                    .partNumber(partNumber).title(title).topic(chosenTopic)
                    .audioUrl("/api/v1/listening/audio/generated_" + System.currentTimeMillis() + ".mp3")
                    .transcriptText(script).durationSeconds(180 + partNumber * 60).build();

            List<ListeningQuestion> questions = new ArrayList<>();
            JsonNode questionsNode = root.path("questions");
            if (questionsNode.isArray()) {
                for (JsonNode qNode : questionsNode) {
                    int questionNumber = qNode.path("questionNumber").asInt();
                    String typeStr = qNode.path("questionType").asText("").toUpperCase();
                    QuestionType questionType = (typeStr.contains("MCQ") || typeStr.contains("MULTIPLE_CHOICE"))
                            ? QuestionType.MCQ : QuestionType.FILL_BLANK;

                    ListeningQuestion question = ListeningQuestion.builder()
                            .part(part).questionType(questionType)
                            .questionText(qNode.path("questionText").asText())
                            .correctAnswer(qNode.path("correctAnswer").asText())
                            .orderIndex(questionNumber).build();

                    List<QuestionOption> options = new ArrayList<>();
                    JsonNode optionsNode = qNode.path("options");
                    if (questionType == QuestionType.MCQ && optionsNode.isArray() && !optionsNode.isEmpty()) {
                        int optIndex = 1;
                        for (JsonNode optNode : optionsNode) {
                            String label, content;
                            if (optNode.isObject()) {
                                label = optNode.path("label").asText();
                                content = optNode.path("content").asText();
                            } else {
                                String optText = optNode.asText();
                                label = optText.length() > 0 ? optText.substring(0, 1).toUpperCase() : "";
                                content = optText;
                                if (optText.length() > 2 && (optText.charAt(1) == '.' || optText.charAt(1) == ')')) {
                                    content = optText.substring(2).trim();
                                }
                            }
                            options.add(QuestionOption.builder()
                                    .listeningQuestion(question).label(label).content(content)
                                    .isCorrect(qNode.path("correctAnswer").asText().equalsIgnoreCase(label))
                                    .orderIndex(optIndex++).build());
                        }
                    }
                    question.setOptions(options);
                    questions.add(question);
                }
            }
            part.setQuestions(questions);
            return part;
        } catch (Exception e) {
            log.error("Failed to parse AI listening response: ", e);
            if (e instanceof InvalidAiResponseException) throw (InvalidAiResponseException) e;
            throw new InvalidAiResponseException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    private void validateListeningPart(ListeningPart part) {
        if (part == null) throw new InvalidAiResponseException("Listening part is null");
        if (part.getTranscriptText() == null || part.getTranscriptText().isBlank())
            throw new InvalidAiResponseException("Listening audio script cannot be empty");
        if (part.getQuestions() == null || part.getQuestions().isEmpty())
            throw new InvalidAiResponseException("Listening part must contain questions");
        for (ListeningQuestion q : part.getQuestions()) {
            if (q.getQuestionText() == null || q.getQuestionText().isBlank())
                throw new InvalidAiResponseException("Question text cannot be empty");
            if (q.getCorrectAnswer() == null || q.getCorrectAnswer().isBlank())
                throw new InvalidAiResponseException("Question correct answer cannot be empty");
            if (q.getQuestionType() == QuestionType.MCQ && (q.getOptions() == null || q.getOptions().isEmpty()))
                throw new InvalidAiResponseException("MCQ question is missing options");
        }
    }

    // ========== AI System Prompts ==========

    private static final String AI_ANALYZE_SYSTEM = """
            You are an expert IELTS Listening tutor. Analyze IELTS Listening questions to help students understand why they got answers wrong. Focus on identifying traps, distractors, and paraphrasing techniques used in the audio. Be specific and reference exact parts of the transcript. Return ONLY valid JSON.
            """;

    private static final String AI_VOCAB_SYSTEM = """
            You are a vocabulary expert specializing in IELTS preparation. Extract advanced vocabulary (B2-C2 CEFR level) from listening transcripts. Provide Vietnamese translations and note the context. Return ONLY valid JSON.
            """;
}
