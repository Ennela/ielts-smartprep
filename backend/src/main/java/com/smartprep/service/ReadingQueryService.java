package com.smartprep.service;

import com.smartprep.dto.response.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Transactional(readOnly = true)
    public Page<ReadingQuizResponse> getTemplateList(String topicStr, String difficultyStr, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Topic topic = (topicStr != null && !topicStr.isBlank()) ? parseEnum(Topic.class, topicStr, "Invalid topic") : null;
        Difficulty difficulty = (difficultyStr != null && !difficultyStr.isBlank()) ? parseEnum(Difficulty.class, difficultyStr, "Invalid difficulty") : null;
        return quizRepository.findQuizzesForAdmin(topic, difficulty, "ADMIN", pageRequest)
                .map(this::mapToQuizResponse);
    }

    @Transactional(readOnly = true)
    public List<ReadingHistoryResponse> getHistory(Long userId) {
        List<ScoreHistory> readingHistories = scoreHistoryRepository
                .findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(sh -> sh.getSkillType() == SkillType.READING)
                .collect(Collectors.toList());

        return quizRepository.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(q -> q.getSubmittedAt() != null)
                .map(q -> {
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
    // Public mapping methods (used by sibling services in the same layer)
    // =========================================================================

    public ReadingQuizResponse mapToQuizResponse(ReadingQuiz quiz) {
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
                .moduleType(quiz.getModuleType())
                .passageText(quiz.getPassageText())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .submitted(quiz.getSubmittedAt() != null)
                .createdAt(quiz.getCreatedAt())
                .questions(questionDtos)
                .build();
    }

    public ReadingResultResponse mapToResultResponse(ReadingQuiz quiz) {
        List<ReadingResultResponse.QuestionResultDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> ReadingResultResponse.QuestionResultDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(mapOptions(q.getOptions(), true))
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

    List<QuestionOptionResponse> mapOptions(List<QuestionOption> options, boolean showCorrect) {
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

    public static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String errorMsg) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMsg + ": " + value);
        }
    }
}
