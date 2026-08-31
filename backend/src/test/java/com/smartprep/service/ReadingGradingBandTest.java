package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingSubmitRequest;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.util.IeltsScoringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The band a reading practice quiz awards must come from the shared scale, not from a
 * private table inside the grading service.
 * <p>
 * ReadingGradingService used to hold its own 14-entry BAND_SCORES_13 array indexed by raw
 * correct count. It skipped bands 7.0 and 8.0 entirely (6.5 -> 7.5 -> 8.5) and disagreed
 * with the 40-question table used by full tests and mock tests, so the same accuracy
 * produced a different band depending on which screen the student came from.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingGradingBandTest {

    private static final Long USER_ID = 1L;
    private static final Long QUIZ_ID = 5L;
    private static final int TOTAL_QUESTIONS = 13;

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ReadingQueryService readingQueryService;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks private ReadingGradingService readingGradingService;

    /** A 13-question quiz where the first {@code correct} answers are right. */
    private ReadingQuiz quizWith(int correct) {
        List<ReadingQuestion> questions = new ArrayList<>();
        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            questions.add(ReadingQuestion.builder()
                    .questionId((long) i)
                    .questionType(QuestionType.TFNG)
                    .correctAnswer("TRUE")
                    .orderIndex(i)
                    .build());
        }
        return ReadingQuiz.builder()
                .quizId(QUIZ_ID)
                .user(User.builder().userId(USER_ID).build())
                .moduleType("ACADEMIC")
                .difficulty(Difficulty.PASSAGE_1)
                .questions(questions)
                .build();
    }

    private Map<Long, String> answersWith(int correct) {
        Map<Long, String> answers = new HashMap<>();
        for (int i = 0; i < TOTAL_QUESTIONS; i++) {
            answers.put((long) i, i < correct ? "TRUE" : "FALSE");
        }
        return answers;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 6, 9, 10, 11, 12, 13})
    @DisplayName("practice band equals the shared scale for the same proportion")
    void practiceBand_matchesSharedScale(int correct) {
        ReadingQuiz quiz = quizWith(correct);
        when(quizRepository.findByQuizIdAndUserUserId(QUIZ_ID, USER_ID)).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().userId(USER_ID).build()));
        when(scoreHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReadingSubmitRequest request = new ReadingSubmitRequest();
        request.setAnswers(answersWith(correct));

        readingGradingService.submitQuiz(QUIZ_ID, USER_ID, request);

        BigDecimal expected =
                IeltsScoringUtils.calculateReadingBand(correct, TOTAL_QUESTIONS, "ACADEMIC");
        assertEquals(expected, quiz.getScore(),
                "grading service must use the shared band scale, not a private table");
    }
}
