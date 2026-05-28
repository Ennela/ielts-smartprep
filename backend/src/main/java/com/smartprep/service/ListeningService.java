package com.smartprep.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.TestMode;
import com.smartprep.repository.*;
import com.smartprep.service.ai.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningService {

    private final ListeningPartRepository partRepository;
    private final ListeningTestRepository testRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    // IELTS Listening band score map (out of 40 questions)
    private static final Map<Integer, BigDecimal> BAND_SCORE_MAP = new LinkedHashMap<>();
    static {
        BAND_SCORE_MAP.put(40, new BigDecimal("9.0"));
        BAND_SCORE_MAP.put(39, new BigDecimal("9.0"));
        BAND_SCORE_MAP.put(38, new BigDecimal("8.5"));
        BAND_SCORE_MAP.put(37, new BigDecimal("8.5"));
        BAND_SCORE_MAP.put(36, new BigDecimal("8.0"));
        BAND_SCORE_MAP.put(35, new BigDecimal("8.0"));
        BAND_SCORE_MAP.put(34, new BigDecimal("7.5"));
        BAND_SCORE_MAP.put(33, new BigDecimal("7.5"));
        BAND_SCORE_MAP.put(32, new BigDecimal("7.5"));
        BAND_SCORE_MAP.put(31, new BigDecimal("7.0"));
        BAND_SCORE_MAP.put(30, new BigDecimal("7.0"));
        BAND_SCORE_MAP.put(29, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(28, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(27, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(26, new BigDecimal("6.5"));
        BAND_SCORE_MAP.put(25, new BigDecimal("6.0"));
        BAND_SCORE_MAP.put(24, new BigDecimal("6.0"));
        BAND_SCORE_MAP.put(23, new BigDecimal("6.0"));
        BAND_SCORE_MAP.put(22, new BigDecimal("5.5"));
        BAND_SCORE_MAP.put(21, new BigDecimal("5.5"));
        BAND_SCORE_MAP.put(20, new BigDecimal("5.5"));
        BAND_SCORE_MAP.put(19, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(18, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(17, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(16, new BigDecimal("5.0"));
        BAND_SCORE_MAP.put(15, new BigDecimal("4.5"));
        BAND_SCORE_MAP.put(14, new BigDecimal("4.5"));
        BAND_SCORE_MAP.put(13, new BigDecimal("4.5"));
        BAND_SCORE_MAP.put(12, new BigDecimal("4.0"));
        BAND_SCORE_MAP.put(11, new BigDecimal("4.0"));
        BAND_SCORE_MAP.put(10, new BigDecimal("4.0"));
    }

    // ========== List Parts ==========

    @Transactional(readOnly = true)
    public List<ListeningPartResponse> getAllParts() {
        return partRepository.findAllByOrderByPartNumberAscPartIdAsc().stream()
                .map(this::toPartResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListeningPartResponse getPartById(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found"));
        return toPartResponse(part);
    }

    // ========== Mock Test Assembly ==========

    @Transactional(readOnly = true)
    public List<ListeningPartResponse> assembleMockTest(Long userId) {
        // Get parts used by user in last 7 days
        List<Long> recentPartIds = testRepository.findRecentPartIds(
                userId, LocalDateTime.now().minusDays(7));

        List<ListeningPartResponse> selectedParts = new ArrayList<>();
        Random random = new Random();

        for (int partNum = 1; partNum <= 4; partNum++) {
            List<ListeningPart> candidates = partRepository.findByPartNumberOrderByPartIdAsc(partNum);
            // Filter out recently used parts
            List<ListeningPart> filtered = candidates.stream()
                    .filter(p -> !recentPartIds.contains(p.getPartId()))
                    .collect(Collectors.toList());
            // If all filtered out, use all candidates
            if (filtered.isEmpty()) filtered = candidates;
            if (filtered.isEmpty()) continue;

            ListeningPart chosen = filtered.get(random.nextInt(filtered.size()));
            selectedParts.add(toPartResponse(chosen));
        }

        return selectedParts;
    }

    // ========== Submit Test ==========

    @Transactional
    public ListeningTestResponse submitTest(Long userId, ListeningSubmitRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TestMode mode = TestMode.valueOf(request.getTestMode().toUpperCase());

        // Load all parts and their questions
        List<ListeningPart> parts = request.getPartIds().stream()
                .map(id -> partRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Part not found: " + id)))
                .collect(Collectors.toList());

        // Grade answers
        int totalQuestions = 0;
        int correctCount = 0;
        List<ListeningTestResponse.PartResult> partResults = new ArrayList<>();

        for (ListeningPart part : parts) {
            List<ListeningTestResponse.QuestionResult> questionResults = new ArrayList<>();

            for (ListeningQuestion q : part.getQuestions()) {
                totalQuestions++;
                String userAnswer = request.getAnswers().getOrDefault(q.getQuestionId(), "");
                boolean isCorrect = checkAnswer(q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                if (isCorrect) correctCount++;

                questionResults.add(ListeningTestResponse.QuestionResult.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .correctAnswer(q.getCorrectAnswer())
                        .userAnswer(userAnswer)
                        .isCorrect(isCorrect)
                        .orderIndex(q.getOrderIndex())
                        .build());
            }

            partResults.add(ListeningTestResponse.PartResult.builder()
                    .partId(part.getPartId())
                    .partNumber(part.getPartNumber())
                    .title(part.getTitle())
                    .topic(part.getTopic())
                    .audioUrl(part.getAudioUrl())
                    .transcriptText(part.getTranscriptText())
                    .questions(questionResults)
                    .build());
        }

        // Calculate band score
        BigDecimal bandScore = calculateBandScore(correctCount, totalQuestions);

        // Save test
        ListeningTest test = ListeningTest.builder()
                .user(user)
                .testMode(mode)
                .score(bandScore)
                .totalQuestions(totalQuestions)
                .correctAnswers(correctCount)
                .build();

        // Save test parts with user answers
        List<ListeningTestPart> testParts = new ArrayList<>();
        for (ListeningPart part : parts) {
            Map<Long, String> partAnswers = new HashMap<>();
            for (ListeningQuestion q : part.getQuestions()) {
                String userAns = request.getAnswers().getOrDefault(q.getQuestionId(), "");
                partAnswers.put(q.getQuestionId(), userAns);
            }
            String answersJson;
            try {
                answersJson = objectMapper.writeValueAsString(partAnswers);
            } catch (Exception e) {
                answersJson = "{}";
            }

            ListeningTestPart tp = ListeningTestPart.builder()
                    .test(test)
                    .part(part)
                    .userAnswersJson(answersJson)
                    .build();
            testParts.add(tp);
        }
        test.setTestParts(testParts);
        test = testRepository.save(test);

        // Save score history
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(SkillType.LISTENING)
                .score(bandScore)
                .build();
        scoreHistoryRepository.save(history);

        return ListeningTestResponse.builder()
                .testId(test.getTestId())
                .testMode(mode.name())
                .score(bandScore)
                .totalQuestions(totalQuestions)
                .correctAnswers(correctCount)
                .submittedAt(test.getSubmittedAt())
                .parts(partResults)
                .build();
    }

    // ========== History ==========

    @Transactional(readOnly = true)
    public List<ListeningHistoryResponse> getHistory(Long userId) {
        return testRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(t -> ListeningHistoryResponse.builder()
                        .testId(t.getTestId())
                        .testMode(t.getTestMode().name())
                        .score(t.getScore())
                        .totalQuestions(t.getTotalQuestions())
                        .correctAnswers(t.getCorrectAnswers())
                        .submittedAt(t.getSubmittedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ========== AI Post-Analysis ==========

    @Transactional(readOnly = true)
    public Map<String, Object> analyzeQuestion(Long questionId) {
        // Find the question and its part
        ListeningPart part = partRepository.findAll().stream()
                .filter(p -> p.getQuestions().stream().anyMatch(q -> q.getQuestionId().equals(questionId)))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        ListeningQuestion question = part.getQuestions().stream()
                .filter(q -> q.getQuestionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        String prompt = String.format(
                "Analyze this IELTS Listening question. The correct answer is '%s'.\n\n" +
                "Transcript:\n%s\n\nQuestion: %s\n\n" +
                "Provide analysis in JSON format:\n" +
                "{\n" +
                "  \"correctAnswerLocation\": \"<exact quote from transcript containing the answer>\",\n" +
                "  \"trapExplanation\": \"<explain any distractors or traps in the audio>\",\n" +
                "  \"paraphraseAnalysis\": \"<explain any paraphrasing between question and transcript>\",\n" +
                "  \"tip\": \"<practical tip to avoid this type of mistake>\"\n" +
                "}\n\nReturn ONLY valid JSON.",
                question.getCorrectAnswer(),
                part.getTranscriptText(),
                question.getQuestionText());

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
                "Scan this IELTS listening transcript and extract advanced vocabulary " +
                "(B2 to C2 level CEFR). Return JSON:\n" +
                "{\n" +
                "  \"vocabularies\": [\n" +
                "    {\n" +
                "      \"word\": \"<word or phrase>\",\n" +
                "      \"partOfSpeech\": \"<noun/verb/adj/adv>\",\n" +
                "      \"vietnameseMeaning\": \"<Vietnamese translation>\",\n" +
                "      \"contextExample\": \"<sentence from transcript containing the word>\",\n" +
                "      \"level\": \"<B2/C1/C2>\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\nTranscript:\n%s\n\nReturn ONLY valid JSON. Extract at least 8 words.",
                part.getTranscriptText());

        String response = geminiClient.generate(AI_VOCAB_SYSTEM, prompt);
        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            throw new InvalidAiResponseException("Failed to parse vocabulary response: " + e.getMessage());
        }
    }

    // ========== Answer Checking ==========

    private boolean checkAnswer(String correct, String userAnswer, String questionType) {
        if (userAnswer == null || userAnswer.isBlank()) return false;

        String cleanCorrect = correct.trim().toLowerCase();
        String cleanUser = userAnswer.trim().toLowerCase();

        if ("MCQ".equals(questionType)) {
            return cleanCorrect.equals(cleanUser);
        }

        // FILL_BLANK: case-insensitive, trim, accept common spelling variants
        if (cleanCorrect.equals(cleanUser)) return true;

        // Accept common variants
        String normalizedCorrect = normalizeSpelling(cleanCorrect);
        String normalizedUser = normalizeSpelling(cleanUser);
        return normalizedCorrect.equals(normalizedUser);
    }

    private String normalizeSpelling(String s) {
        return s.replace("organisation", "organization")
                .replace("centre", "center")
                .replace("colour", "color")
                .replace("favour", "favor")
                .replace("behaviour", "behavior")
                .replace("travelling", "traveling")
                .replace("cancelled", "canceled")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ========== Band Score Calculation ==========

    private BigDecimal calculateBandScore(int correct, int total) {
        if (total == 0) return BigDecimal.ZERO;

        // For mock tests with 40 questions, use the standard map
        if (total == 40) {
            return BAND_SCORE_MAP.getOrDefault(Math.min(correct, 40), new BigDecimal("3.5"));
        }

        // For practice mode (fewer questions), scale proportionally
        double scaled = ((double) correct / total) * 40.0;
        int scaledInt = (int) Math.round(scaled);
        return BAND_SCORE_MAP.getOrDefault(Math.min(scaledInt, 40), new BigDecimal("3.5"));
    }

    // ========== Mapping Helpers ==========

    private ListeningPartResponse toPartResponse(ListeningPart part) {
        List<ListeningPartResponse.QuestionDto> questions = part.getQuestions().stream()
                .sorted(Comparator.comparingInt(ListeningQuestion::getOrderIndex))
                .map(q -> ListeningPartResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .orderIndex(q.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        return ListeningPartResponse.builder()
                .partId(part.getPartId())
                .partNumber(part.getPartNumber())
                .title(part.getTitle())
                .topic(part.getTopic())
                .audioUrl(part.getAudioUrl())
                .durationSeconds(part.getDurationSeconds())
                .questionCount(questions.size())
                .questions(questions)
                .build();
    }

    // ========== AI System Prompts ==========

    private static final String AI_ANALYZE_SYSTEM = """
            You are an expert IELTS Listening tutor. Analyze IELTS Listening questions to help students understand why they got answers wrong. Focus on identifying traps, distractors, and paraphrasing techniques used in the audio. Be specific and reference exact parts of the transcript. Return ONLY valid JSON.
            """;

    private static final String AI_VOCAB_SYSTEM = """
            You are a vocabulary expert specializing in IELTS preparation. Extract advanced vocabulary (B2-C2 CEFR level) from listening transcripts. Provide Vietnamese translations and note the context. Return ONLY valid JSON.
            """;
}
