package com.smartprep.service;

import com.smartprep.dto.response.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuestionRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import com.smartprep.service.util.QuestionOptionMapper;
import com.smartprep.service.util.UserPageRequests;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only queries and DTO mapping for Reading quizzes.
 * Extracted from the original ReadingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingQueryService {

    private final ReadingQuizRepository quizRepository;
    private final ReadingQuestionRepository questionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;

    @Transactional(readOnly = true)
    public ReadingQuizResponse getQuiz(Long quizId, Long userId) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        return mapToQuizResponse(quiz);
    }

    @Transactional(readOnly = true)
    public ReadingResultResponse getResult(Long quizId, Long userId) {
        ReadingQuiz quiz = quizRepository.findByQuizIdAndUserUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (quiz.getSubmittedAt() == null) {
            throw new IllegalArgumentException("Quiz has not been submitted yet");
        }
        return mapToResultResponse(quiz);
    }

    /** How much of a passage the template list carries: enough for the card's preview. */
    static final int TEMPLATE_PREVIEW_CHARS = 400;

    /**
     * The template catalogue, as the list page needs it: metadata, a passage preview and a
     * question count -- never the questions themselves.
     *
     * <p>This used to map each quiz through {@code mapToQuizResponse}, which walks every
     * question and every option: for a page of 20 papers that was roughly 280 queries and
     * 20 full passages of text to draw 20 cards. It is now the page query, its count, and
     * one aggregate for the question counts. The questions are loaded by the start endpoint,
     * which is the only place they are used.
     */
    @Transactional(readOnly = true)
    public Page<ReadingQuizResponse> getTemplateList(String topicStr, String difficultyStr, int page, int size) {
        PageRequest pageRequest = UserPageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Topic topic = (topicStr != null && !topicStr.isBlank()) ? parseEnum(Topic.class, topicStr, "Invalid topic") : null;
        Difficulty difficulty = (difficultyStr != null && !difficultyStr.isBlank()) ? parseEnum(Difficulty.class, difficultyStr, "Invalid difficulty") : null;
        Page<ReadingQuiz> quizzes = quizRepository.findQuizzesForAdmin(topic, difficulty, "ADMIN", pageRequest);

        List<Long> ids = quizzes.getContent().stream().map(ReadingQuiz::getQuizId).collect(Collectors.toList());
        Map<Long, Integer> questionCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : questionRepository.countByQuizIds(ids)) {
                questionCounts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }

        return quizzes.map(quiz -> ReadingQuizResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .moduleType(quiz.getModuleType())
                .passageText(preview(quiz.getPassageText()))
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .submitted(false)
                .createdAt(quiz.getCreatedAt())
                .totalQuestions(questionCounts.getOrDefault(quiz.getQuizId(), 0))
                .build());
    }

    private static String preview(String passage) {
        if (passage == null || passage.length() <= TEMPLATE_PREVIEW_CHARS) {
            return passage;
        }
        return passage.substring(0, TEMPLATE_PREVIEW_CHARS) + "…";
    }

    @Transactional(readOnly = true)
    public Page<ReadingHistoryResponse> getHistory(Long userId, int page, int size) {
        Page<ReadingQuiz> quizzes = quizRepository.findSubmittedByUser(userId,
                UserPageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // Only the history rows that could pair with this page, not the user's whole history.
        List<ScoreHistory> readingHistories = historyWindow(userId, quizzes.getContent().stream()
                .map(ReadingQuiz::getSubmittedAt).collect(Collectors.toList()));

        return quizzes.map(q -> {
                    Long historyId = readingHistories.stream()
                            .filter(sh -> sh.getScore().compareTo(q.getScore()) == 0)
                            .filter(sh -> !sh.getRecordedAt().isBefore(q.getSubmittedAt().minusSeconds(5)))
                            .filter(sh -> !sh.getRecordedAt().isAfter(q.getSubmittedAt().plusSeconds(5)))
                            .map(ScoreHistory::getHistoryId)
                            .findFirst()
                            .orElse(null);

                    // Extract timer data from matched ScoreHistory
                    ScoreHistory matchedHistory = readingHistories.stream()
                            .filter(sh -> sh.getScore().compareTo(q.getScore()) == 0)
                            .filter(sh -> !sh.getRecordedAt().isBefore(q.getSubmittedAt().minusSeconds(5)))
                            .filter(sh -> !sh.getRecordedAt().isAfter(q.getSubmittedAt().plusSeconds(5)))
                            .findFirst().orElse(null);

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
                            .timeSpentSeconds(matchedHistory != null ? matchedHistory.getTimeSpentSeconds() : null)
                            .autoSubmitted(matchedHistory != null ? matchedHistory.getAutoSubmitted() : null)
                            .build();
                });
    }

    /** The READING history rows inside five seconds of any submission on this page. */
    private List<ScoreHistory> historyWindow(Long userId, List<LocalDateTime> submittedAts) {
        if (submittedAts.isEmpty()) {
            return List.of();
        }
        LocalDateTime from = submittedAts.stream().min(LocalDateTime::compareTo).get().minusSeconds(5);
        LocalDateTime to = submittedAts.stream().max(LocalDateTime::compareTo).get().plusSeconds(5);
        return scoreHistoryRepository.findByUserUserIdAndSkillTypeAndRecordedAtBetween(
                userId, SkillType.READING, from, to);
    }

    // =========================================================================
    // Public mapping methods (used by sibling services in the same layer)
    // =========================================================================

    public ReadingQuizResponse mapToQuizResponse(ReadingQuiz quiz) {
        List<ReadingQuizResponse.QuestionDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> ReadingQuizResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(QuestionOptionMapper.mapForExam(q.getOptions()))
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
                .moduleType(quiz.getModuleType())
                .passageText(quiz.getPassageText())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .submitted(quiz.getSubmittedAt() != null)
                .createdAt(quiz.getCreatedAt())
                .totalQuestions(questionDtos.size())
                .questions(questionDtos)
                .build();
    }

    public ReadingResultResponse mapToResultResponse(ReadingQuiz quiz) {
        List<ReadingResultResponse.QuestionResultDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> ReadingResultResponse.QuestionResultDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(QuestionOptionMapper.mapForReview(q.getOptions()))
                        .orderIndex(q.getOrderIndex())
                        .correctAnswer(q.getCorrectAnswer())
                        .userAnswer(q.getUserAnswer())
                        .correct(IeltsScoringUtils.isReadingCorrect(q.getQuestionType(), q.getCorrectAnswer(), q.getUserAnswer()))
                        .explanation(q.getExplanation())
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
                        .evidenceText(q.getEvidenceText())
                        .evidenceOffset(q.getEvidenceOffset())
                        .evidenceLength(q.getEvidenceLength())
                        .build())
                .collect(Collectors.toList());

        return ReadingResultResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .moduleType(quiz.getModuleType())
                .passageText(quiz.getPassageText())
                .correctAnswers(quiz.getCorrectAnswers())
                .totalQuestions(quiz.getTotalQuestions())
                .bandScore(quiz.getScore())
                .createdAt(quiz.getCreatedAt())
                .submittedAt(quiz.getSubmittedAt())
                .questions(questionDtos)
                .build();
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String errorMsg) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMsg + ": " + value);
        }
    }
}
