package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.AnalyticsThresholdConfig;
import com.smartprep.dto.response.MockTestAnalyticsResponse.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The expensive half of mock-test analytics: everything derived from the answers and the
 * paper's questions, per skill.
 *
 * <p>This is the part worth caching. Computing it walks the paper's lazy collections --
 * every listening part, every reading passage, every question and its options -- which is
 * a dozen queries per request. It is also the part that does not change: once a sitting is
 * graded, its answers and the questions they were graded against are fixed. Everything the
 * caller adds afterwards (targets, the attempt timeline, recommendations) is cheap and
 * depends on state that does move, so it is deliberately computed fresh on every request.
 *
 * <p>Cache key is {@code submissionId:status}. Status is part of the key rather than a
 * reason to evict: a submission passes GRADING -> COMPLETED (or FAILED, and back to GRADING
 * on a retry), and the writing analytics differ in each, so each state simply has its own
 * entry and the old one ages out. The only way a cached entry goes stale is an admin editing
 * the paper's questions after the sitting, which the TTL in {@code CacheConfig} bounds.
 *
 * <p>Lives on its own bean because {@code @Cacheable} is applied by a proxy: a call from
 * one method of a bean to another method of the same bean does not go through it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestAnalyticsCalculator {

    public static final String CACHE_NAME = "mockTestAnalytics";

    private final MockTestSubmissionRepository submissionRepository;
    private final MockTestSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final AnalyticsThresholdConfig thresholds;

    private static final Map<String, String> QUESTION_TYPE_LABELS = Map.ofEntries(
            Map.entry("MCQ", "Multiple choice"),
            Map.entry("TFNG", "True / False / Not Given"),
            Map.entry("YNNG", "Yes / No / Not Given"),
            Map.entry("FILL_BLANK", "Fill in the blank"),
            Map.entry("SENTENCE_COMPLETION", "Sentence completion"),
            Map.entry("SUMMARY_COMPLETION", "Summary completion"),
            Map.entry("MATCHING_HEADINGS", "Matching headings"),
            Map.entry("MATCHING_INFORMATION", "Matching information"),
            Map.entry("MATCHING_FEATURES", "Matching features"),
            Map.entry("MATCHING_SENTENCE_ENDINGS", "Matching sentence endings"),
            Map.entry("DIAGRAM_LABEL_COMPLETION", "Diagram label completion"),
            Map.entry("SHORT_ANSWER", "Short answer"));

    /**
     * The cached value. Plain data with a no-args constructor so the Redis JSON serializer
     * can read it back; {@code writing} is null unless the submission was COMPLETED.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionAnalytics {
        private ListeningAnalytics listening;
        private ReadingAnalytics reading;
        private WritingAnalytics writing;
    }

    /**
     * Compute, or fetch from cache, the question-level analytics for one submission.
     *
     * <p>Ownership is not checked here -- the caller has already done that on the
     * submission it holds -- and nothing user-specific is in the value, so a hit is safe to
     * serve to whoever passed the ownership check.
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "#submissionId + ':' + #status")
    @Transactional(readOnly = true)
    public QuestionAnalytics compute(Long submissionId, SubmissionStatus status) {
        MockTestSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        MockTest mockTest = sub.getMockTest();
        Map<String, String> answers = loadAnswers(sub.getSessionId());

        return QuestionAnalytics.builder()
                .listening(analyseListening(mockTest, answers))
                .reading(analyseReading(mockTest, answers))
                .writing(status == SubmissionStatus.COMPLETED ? analyseWriting(sub) : null)
                .build();
    }

    // ========== Answers ==========

    private Map<String, String> loadAnswers(Long sessionId) {
        String progressJson = sessionRepository.findById(sessionId)
                .map(MockTestSession::getProgressJson)
                .orElse(null);
        if (progressJson == null || progressJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(progressJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            // Same tolerance as getSubmission: an unreadable payload reads as "nothing
            // answered" rather than hiding the scores that were already computed.
            log.error("Failed to parse progress JSON for analytics of session {}", sessionId, e);
            return Map.of();
        }
    }

    // ========== Listening / Reading ==========

    /** One graded question, flattened so the two skills can share the grouping code. */
    private record AnsweredQuestion(Long questionId, Integer orderIndex, String section,
                                    String questionType, String questionText,
                                    String userAnswer, String correctAnswer, boolean correct) {}

    private ListeningAnalytics analyseListening(MockTest mockTest, Map<String, String> answers) {
        List<AnsweredQuestion> graded = new ArrayList<>();
        for (ListeningPart part : mockTest.getListeningParts()) {
            if (part == null) continue;
            String section = "Part " + part.getPartNumber();
            for (ListeningQuestion q : part.getQuestions()) {
                String userAnswer = answers.get(q.getQuestionId().toString());
                boolean correct = IeltsScoringUtils.isListeningCorrect(
                        q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                graded.add(new AnsweredQuestion(q.getQuestionId(), q.getOrderIndex(), section,
                        q.getQuestionType().name(), q.getQuestionText(),
                        userAnswer, q.getCorrectAnswer(), correct));
            }
        }

        int correct = (int) graded.stream().filter(AnsweredQuestion::correct).count();
        double accuracy = percent(correct, graded.size());
        return ListeningAnalytics.builder()
                .correct(correct)
                .total(graded.size())
                .accuracy(accuracy)
                .level(graded.isEmpty() ? null : thresholds.classifyAccuracy(accuracy))
                // Parts keep their exam order; a candidate reads "Part 3" as a place, not a rank.
                .byPart(groupBy(graded, AnsweredQuestion::section, Function.identity(),
                        Comparator.comparing(GroupAccuracy::getKey)))
                .byQuestionType(groupByQuestionType(graded))
                .wrongQuestions(wrongQuestions(graded))
                .build();
    }

    private ReadingAnalytics analyseReading(MockTest mockTest, Map<String, String> answers) {
        List<AnsweredQuestion> graded = new ArrayList<>();
        int passageNo = 0;
        for (ReadingQuiz quiz : mockTest.getReadingQuizzes()) {
            if (quiz == null) continue;
            passageNo++;
            String section = "Passage " + passageNo;
            for (ReadingQuestion q : quiz.getQuestions()) {
                String userAnswer = answers.get(q.getQuestionId().toString());
                boolean correct = IeltsScoringUtils.isReadingCorrect(
                        q.getQuestionType(), q.getCorrectAnswer(), userAnswer);
                graded.add(new AnsweredQuestion(q.getQuestionId(), q.getOrderIndex(), section,
                        q.getQuestionType().name(), q.getQuestionText(),
                        userAnswer, q.getCorrectAnswer(), correct));
            }
        }

        int correct = (int) graded.stream().filter(AnsweredQuestion::correct).count();
        double accuracy = percent(correct, graded.size());
        return ReadingAnalytics.builder()
                .correct(correct)
                .total(graded.size())
                .accuracy(accuracy)
                .level(graded.isEmpty() ? null : thresholds.classifyAccuracy(accuracy))
                .byQuestionType(groupByQuestionType(graded))
                .wrongQuestions(wrongQuestions(graded))
                .build();
    }

    /** Worst type first, so the top of the list is the thing to work on. */
    private List<GroupAccuracy> groupByQuestionType(List<AnsweredQuestion> graded) {
        return groupBy(graded, AnsweredQuestion::questionType,
                key -> QUESTION_TYPE_LABELS.getOrDefault(key, key),
                Comparator.comparingDouble(GroupAccuracy::getAccuracy)
                        .thenComparing(GroupAccuracy::getTotal, Comparator.reverseOrder())
                        .thenComparing(GroupAccuracy::getKey));
    }

    private List<GroupAccuracy> groupBy(List<AnsweredQuestion> graded,
                                        Function<AnsweredQuestion, String> keyOf,
                                        Function<String, String> labelOf,
                                        Comparator<GroupAccuracy> order) {
        Map<String, List<AnsweredQuestion>> groups = graded.stream()
                .collect(Collectors.groupingBy(keyOf, LinkedHashMap::new, Collectors.toList()));

        return groups.entrySet().stream()
                .map(e -> {
                    int correct = (int) e.getValue().stream().filter(AnsweredQuestion::correct).count();
                    double accuracy = percent(correct, e.getValue().size());
                    return GroupAccuracy.builder()
                            .key(e.getKey())
                            .label(labelOf.apply(e.getKey()))
                            .correct(correct)
                            .total(e.getValue().size())
                            .accuracy(accuracy)
                            .level(thresholds.classifyAccuracy(accuracy))
                            .build();
                })
                .sorted(order)
                .collect(Collectors.toList());
    }

    private List<WrongQuestion> wrongQuestions(List<AnsweredQuestion> graded) {
        return graded.stream()
                .filter(q -> !q.correct())
                .map(q -> WrongQuestion.builder()
                        .questionId(q.questionId())
                        .orderIndex(q.orderIndex())
                        .section(q.section())
                        .questionType(q.questionType())
                        .questionText(q.questionText())
                        .userAnswer(q.userAnswer())
                        .correctAnswer(q.correctAnswer())
                        .build())
                .collect(Collectors.toList());
    }

    // ========== Writing ==========

    private WritingAnalytics analyseWriting(MockTestSubmission sub) {
        WritingSubmission t1 = sub.getWritingTask1Submission();
        WritingSubmission t2 = sub.getWritingTask2Submission();
        if (t1 == null || t2 == null) {
            // COMPLETED with no essays should not happen, but a null here must not take the
            // whole dashboard down with it.
            log.warn("Submission {} is COMPLETED but is missing a writing task", sub.getSubmissionId());
            return null;
        }

        List<CriterionScore> combined = new ArrayList<>();
        combined.add(combinedCriterion("TASK_RESPONSE", "Task Response",
                t1.getTaskResponseScore(), t2.getTaskResponseScore()));
        combined.add(combinedCriterion("COHERENCE_COHESION", "Coherence & Cohesion",
                t1.getCoherenceScore(), t2.getCoherenceScore()));
        combined.add(combinedCriterion("LEXICAL_RESOURCE", "Lexical Resource",
                t1.getLexicalScore(), t2.getLexicalScore()));
        combined.add(combinedCriterion("GRAMMAR", "Grammatical Range & Accuracy",
                t1.getGrammarScore(), t2.getGrammarScore()));

        BigDecimal band = sub.getWritingScore();
        return WritingAnalytics.builder()
                .band(band)
                .level(band == null ? null : thresholds.classifyBand(band))
                .task1(writingTask(t1))
                .task2(writingTask(t2))
                .criteria(combined)
                .build();
    }

    private WritingTask writingTask(WritingSubmission ws) {
        return WritingTask.builder()
                .submissionId(ws.getSubmissionId())
                .band(ws.getOverallBand())
                .wordCount(ws.getWordCount())
                .criteria(List.of(
                        criterion("TASK_RESPONSE", "Task Response", ws.getTaskResponseScore()),
                        criterion("COHERENCE_COHESION", "Coherence & Cohesion", ws.getCoherenceScore()),
                        criterion("LEXICAL_RESOURCE", "Lexical Resource", ws.getLexicalScore()),
                        criterion("GRAMMAR", "Grammatical Range & Accuracy", ws.getGrammarScore())))
                .build();
    }

    private CriterionScore criterion(String key, String label, BigDecimal band) {
        return CriterionScore.builder()
                .key(key)
                .label(label)
                .band(band)
                .level(band == null ? null : thresholds.classifyBand(band))
                .build();
    }

    /** Task 2 counts double, the same weighting the writing band itself uses. */
    private CriterionScore combinedCriterion(String key, String label, BigDecimal t1, BigDecimal t2) {
        if (t1 == null || t2 == null) {
            return criterion(key, label, null);
        }
        BigDecimal weighted = t1.add(t2.multiply(BigDecimal.valueOf(2)))
                .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
        return criterion(key, label, IeltsScoringUtils.roundOverallBand(weighted));
    }

    private static double percent(int correct, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((double) correct / total * 1000.0) / 10.0;
    }
}
