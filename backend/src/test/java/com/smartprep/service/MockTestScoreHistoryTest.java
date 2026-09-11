package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.*;
import com.smartprep.service.ai.MockTestAsyncGrader;
import com.smartprep.service.ai.MockTestGradingPersistence;
import com.smartprep.service.ai.WritingGradingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A full mock test now leaves the same trail in score_history as three practice tests:
 * Listening and Reading at submit time, Writing once the asynchronous grade lands. Before
 * this the dashboard, trends, weakness analysis and adaptive difficulty never saw a mock
 * test at all.
 */
class MockTestScoreHistoryTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long SUBMISSION_ID = 500L;

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("on submit")
    class OnSubmit {

        @Mock private MockTestRepository mockTestRepository;
        @Mock private MockTestSessionRepository sessionRepository;
        @Mock private MockTestSubmissionRepository submissionRepository;
        @Mock private ListeningTestRepository listeningTestRepository;
        @Mock private ScoreHistoryRepository scoreHistoryRepository;
        @Mock private UserRepository userRepository;
        @Spy private ObjectMapper objectMapper = new ObjectMapper();
        @Mock private MockTestAsyncGrader asyncGrader;
        @Mock private ExamDurationConfig durationConfig;

        @InjectMocks private MockTestService mockTestService;

        private MockTestSession sessionInWriting() {
            User user = User.builder().userId(USER_ID).build();
            MockTest paper = MockTest.builder()
                    .mockTestId(1L)
                    .title("Fixture")
                    .sections(List.of(MockTestSection.builder().sectionType(SkillType.WRITING)
                            .durationSeconds(3600).sectionOrder(3).build()))
                    .listeningParts(List.of(ListeningPart.builder().partId(1L).partNumber(1).questions(List.of(
                            ListeningQuestion.builder().questionId(11L).orderIndex(1).questionType(QuestionType.MCQ)
                                    .questionText("L1").correctAnswer("A").build(),
                            ListeningQuestion.builder().questionId(12L).orderIndex(2).questionType(QuestionType.FILL_BLANK)
                                    .questionText("L2").correctAnswer("library").build())).build()))
                    .readingQuizzes(List.of(ReadingQuiz.builder().quizId(1L).moduleType("GENERAL").questions(List.of(
                            ReadingQuestion.builder().questionId(21L).orderIndex(1).questionType(QuestionType.TFNG)
                                    .questionText("R1").correctAnswer("TRUE").explanation("because").build(),
                            ReadingQuestion.builder().questionId(22L).orderIndex(2).questionType(QuestionType.TFNG)
                                    .questionText("R2").correctAnswer("FALSE").build(),
                            ReadingQuestion.builder().questionId(23L).orderIndex(3).questionType(QuestionType.SHORT_ANSWER)
                                    .questionText("R3").correctAnswer("bridge").build())).build()))
                    .writingPrompts(List.of())
                    .build();
            return MockTestSession.builder()
                    .sessionId(SESSION_ID).user(user).mockTest(paper)
                    .status(SessionStatus.IN_PROGRESS).currentSection(SkillType.WRITING)
                    .startedAt(LocalDateTime.now().minusMinutes(5)).sectionStartedAt(LocalDateTime.now().minusMinutes(5))
                    .timeRemainingSeconds(3000)
                    .progressJson("{\"11\":\"A\",\"12\":\"Library\",\"21\":\"TRUE\",\"23\":\"river\"}")
                    .build();
        }

        @Test
        @DisplayName("records a Listening and a Reading row, linked to the submission, with every answer snapshotted")
        void submitRecordsListeningAndReading() {
            MockTestSession session = sessionInWriting();
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(listeningTestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(submissionRepository.save(any(MockTestSubmission.class))).thenAnswer(i -> {
                MockTestSubmission s = i.getArgument(0);
                s.setSubmissionId(SUBMISSION_ID);
                return s;
            });

            MockTestSubmitRequest request = new MockTestSubmitRequest();
            request.setProgressJson(session.getProgressJson());
            mockTestService.submitExam(USER_ID, SESSION_ID, request);

            ArgumentCaptor<ScoreHistory> captor = ArgumentCaptor.forClass(ScoreHistory.class);
            verify(scoreHistoryRepository, times(2)).save(captor.capture());
            Map<SkillType, ScoreHistory> rows = captor.getAllValues().stream()
                    .collect(Collectors.toMap(ScoreHistory::getSkillType, h -> h));
            assertEquals(2, rows.size());

            ScoreHistory listening = rows.get(SkillType.LISTENING);
            assertNotNull(listening);
            assertEquals(MockTestService.MOCK_TEST_DIFFICULTY, listening.getDifficulty());
            assertEquals(SUBMISSION_ID, listening.getMockTestSubmission().getSubmissionId());
            assertEquals(USER_ID, listening.getUser().getUserId());
            // Both listening answers correct: "A" and a case-insensitive "Library".
            assertEquals(2, listening.getUserAnswers().size());
            assertTrue(listening.getUserAnswers().stream().allMatch(UserAnswer::getIsCorrect));
            assertEquals(List.of(1, 2), listening.getUserAnswers().stream().map(UserAnswer::getQuestionNo).collect(Collectors.toList()));
            assertTrue(listening.getUserAnswers().stream().allMatch(a -> a.getScoreHistory() == listening));

            ScoreHistory reading = rows.get(SkillType.READING);
            assertNotNull(reading);
            assertEquals(MockTestService.MOCK_TEST_DIFFICULTY, reading.getDifficulty());
            // The paper's real module type, not an assumed ACADEMIC.
            assertEquals("GENERAL", reading.getModuleType());
            assertEquals(3, reading.getUserAnswers().size());
            // R1 correct, R2 unanswered (stored as "" like practice does), R3 wrong.
            UserAnswer r2 = reading.getUserAnswers().get(1);
            assertEquals("", r2.getUserAnswer());
            assertFalse(r2.getIsCorrect());
            assertEquals("FALSE", r2.getCorrectAnswer());
            assertEquals("because", reading.getUserAnswers().get(0).getExplanation());
            assertEquals(1, reading.getUserAnswers().stream().filter(UserAnswer::getIsCorrect).count());
            // The band on the row is the band on the submission.
            MockTestSubmission submission = listening.getMockTestSubmission();
            assertEquals(submission.getListeningScore(), listening.getScore());
            assertEquals(submission.getReadingScore(), reading.getScore());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("when the writing grade lands")
    class OnWritingGraded {

        @Mock private MockTestSubmissionRepository submissionRepository;
        @Mock private WritingSubmissionRepository writingSubmissionRepository;
        @Mock private WritingPromptRepository writingPromptRepository;
        @Mock private UserRepository userRepository;
        @Mock private ScoreHistoryRepository scoreHistoryRepository;

        @InjectMocks private MockTestGradingPersistence persistence;

        private MockTestGradingPersistence.GradingInputs inputs() {
            return MockTestGradingPersistence.GradingInputs.builder()
                    .userId(USER_ID).task1PromptId(7L).task2PromptId(8L)
                    .task1PromptText("t1").task2PromptText("t2").build();
        }

        private WritingGradingService.GradingResult result(String band) {
            return WritingGradingService.GradingResult.builder()
                    .overallBand(new BigDecimal(band))
                    .taskResponse(new BigDecimal(band)).coherence(new BigDecimal(band))
                    .lexical(new BigDecimal(band)).grammar(new BigDecimal(band))
                    .wordCount(250).build();
        }

        private void stubGradedSubmission() {
            MockTestSubmission sub = MockTestSubmission.builder()
                    .submissionId(SUBMISSION_ID).status(SubmissionStatus.GRADING)
                    .listeningScore(new BigDecimal("6.0")).readingScore(new BigDecimal("6.0")).build();
            when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
            when(userRepository.getReferenceById(USER_ID)).thenReturn(User.builder().userId(USER_ID).build());
            when(writingPromptRepository.getReferenceById(anyLong())).thenAnswer(i ->
                    WritingPrompt.builder().promptId(i.getArgument(0)).build());
            when(writingSubmissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        }

        @Test
        @DisplayName("records one Writing row carrying the weighted writing band")
        void recordsWritingOnce() {
            stubGradedSubmission();
            when(scoreHistoryRepository.existsByMockTestSubmissionSubmissionIdAndSkillType(SUBMISSION_ID, SkillType.WRITING))
                    .thenReturn(false);

            // (5.0 + 2 x 6.5) / 3 = 6.0
            persistence.persistResults(SUBMISSION_ID, inputs(), "e1", "e2", result("5.0"), result("6.5"));

            ArgumentCaptor<ScoreHistory> captor = ArgumentCaptor.forClass(ScoreHistory.class);
            verify(scoreHistoryRepository).save(captor.capture());
            ScoreHistory row = captor.getValue();
            assertEquals(SkillType.WRITING, row.getSkillType());
            assertEquals(new BigDecimal("6.0"), row.getScore());
            assertEquals(MockTestService.MOCK_TEST_DIFFICULTY, row.getDifficulty());
            assertEquals(SUBMISSION_ID, row.getMockTestSubmission().getSubmissionId());
            assertEquals(USER_ID, row.getUser().getUserId());
        }

        @Test
        @DisplayName("a re-run of the grade does not record the sitting a second time")
        void regradeDoesNotDuplicate() {
            stubGradedSubmission();
            when(scoreHistoryRepository.existsByMockTestSubmissionSubmissionIdAndSkillType(SUBMISSION_ID, SkillType.WRITING))
                    .thenReturn(true);

            persistence.persistResults(SUBMISSION_ID, inputs(), "e1", "e2", result("6.0"), result("6.0"));

            verify(scoreHistoryRepository, never()).save(any());
            // The essays and the submission itself are still updated as before.
            verify(writingSubmissionRepository, times(2)).save(any());
            verify(submissionRepository).save(argThat(s -> s.getStatus() == SubmissionStatus.COMPLETED));
        }

        @Test
        @DisplayName("the history row is not written when the essays fail to persist")
        void noRowWithoutEssays() {
            stubGradedSubmission();
            when(writingSubmissionRepository.save(any())).thenThrow(new IllegalStateException("db down"));

            assertThrows(IllegalStateException.class,
                    () -> persistence.persistResults(SUBMISSION_ID, inputs(), "e1", "e2", result("6.0"), result("6.0")));

            verify(scoreHistoryRepository, never()).save(any());
            verify(scoreHistoryRepository, never()).existsByMockTestSubmissionSubmissionIdAndSkillType(eq(SUBMISSION_ID), any());
        }
    }
}
