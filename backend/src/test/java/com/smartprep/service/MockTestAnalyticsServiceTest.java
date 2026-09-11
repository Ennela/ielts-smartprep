package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.config.AnalyticsThresholdConfig;
import com.smartprep.dto.response.MockTestAnalyticsResponse;
import com.smartprep.dto.response.MockTestAnalyticsResponse.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.PerformanceLevel;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSessionRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Analytics are arithmetic over the stored answers, so each figure below is checked
 * against a hand-computed value from a small fixed paper: nine reading questions across
 * three types, six listening questions across two parts, and two graded essays.
 */
@ExtendWith(MockitoExtension.class)
class MockTestAnalyticsServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SUBMISSION_ID = 500L;
    private static final Long SESSION_ID = 900L;

    @Mock private MockTestSubmissionRepository submissionRepository;
    @Mock private MockTestSessionRepository sessionRepository;

    private MockTestAnalyticsService service;

    private User owner;
    private MockTest mockTest;

    @BeforeEach
    void setUp() {
        AnalyticsThresholdConfig thresholds = new AnalyticsThresholdConfig();
        ReflectionTestUtils.setField(thresholds, "accuracyWeakBelow", 60.0);
        ReflectionTestUtils.setField(thresholds, "accuracyStrongFrom", 80.0);
        ReflectionTestUtils.setField(thresholds, "bandWeakBelow", new BigDecimal("5.5"));
        ReflectionTestUtils.setField(thresholds, "bandStrongFrom", new BigDecimal("7.0"));
        ReflectionTestUtils.setField(thresholds, "minSampleSize", 3);

        // The calculator is wired directly, without a cache, so these tests exercise the
        // arithmetic; MockTestAnalyticsCacheTest covers the caching contract.
        MockTestAnalyticsCalculator calculator =
                new MockTestAnalyticsCalculator(submissionRepository, sessionRepository, new ObjectMapper(), thresholds);
        service = new MockTestAnalyticsService(submissionRepository, calculator, thresholds);

        owner = User.builder()
                .userId(OWNER_ID)
                .targetListeningScore(new BigDecimal("7.0"))
                .targetReadingScore(new BigDecimal("6.5"))
                .targetWritingScore(null)
                .build();

        mockTest = MockTest.builder()
                .mockTestId(10L)
                .title("Cambridge 19 Test 1")
                .listeningParts(List.of(
                        part(1L, 1, List.of(
                                lq(201L, 1, QuestionType.FILL_BLANK, "library"),
                                lq(202L, 2, QuestionType.FILL_BLANK, "centre"),
                                lq(203L, 3, QuestionType.MCQ, "B"))),
                        part(2L, 2, List.of(
                                lq(204L, 4, QuestionType.MCQ, "A"),
                                lq(205L, 5, QuestionType.FILL_BLANK, "monday"),
                                lq(206L, 6, QuestionType.FILL_BLANK, "9am")))))
                .readingQuizzes(List.of(
                        quiz(1L, List.of(
                                rq(101L, 1, QuestionType.MCQ, "A"),
                                rq(102L, 2, QuestionType.MCQ, "B"),
                                rq(103L, 3, QuestionType.TFNG, "TRUE"),
                                rq(104L, 4, QuestionType.TFNG, "FALSE"),
                                rq(105L, 5, QuestionType.TFNG, "NOT GIVEN"),
                                rq(106L, 6, QuestionType.TFNG, "TRUE"),
                                rq(107L, 7, QuestionType.SHORT_ANSWER, "the river"),
                                rq(108L, 8, QuestionType.SHORT_ANSWER, "bridge"),
                                rq(109L, 9, QuestionType.SHORT_ANSWER, "1990")))))
                .build();
    }

    // Listening: Part 1 3/3, Part 2 1/3. Reading: MCQ 1/2, TFNG 1/4, SHORT_ANSWER 3/3.
    private static final String ANSWERS = """
            {"201":"Library","202":"center","203":"b",
             "204":"C","206":"9am",
             "101":"A","102":"C","103":"TRUE","104":"TRUE","106":"FALSE",
             "107":"River","108":"bridge","109":"1990",
             "w_task1":"essay one","w_task2":"essay two"}
            """;

    private MockTestSubmission completedSubmission() {
        return MockTestSubmission.builder()
                .submissionId(SUBMISSION_ID)
                .user(owner)
                .mockTest(mockTest)
                .sessionId(SESSION_ID)
                .status(SubmissionStatus.COMPLETED)
                .listeningScore(new BigDecimal("6.0"))
                .readingScore(new BigDecimal("5.5"))
                .writingScore(new BigDecimal("5.5"))
                .overallBand(new BigDecimal("5.5"))
                .listeningCorrectAnswers(4)
                .readingCorrectAnswers(5)
                .submittedAt(LocalDateTime.now())
                .writingTask1Submission(essay(701L, "5.5", "5.0", "6.0", "6.0", "5.0"))
                .writingTask2Submission(essay(702L, "6.0", "6.0", "7.0", "6.5", "5.0"))
                .build();
    }

    private MockTestSubmission earlierCompleted() {
        return MockTestSubmission.builder()
                .submissionId(400L)
                .user(owner)
                .mockTest(mockTest)
                .sessionId(800L)
                .status(SubmissionStatus.COMPLETED)
                .listeningScore(new BigDecimal("5.0"))
                .readingScore(new BigDecimal("5.0"))
                .writingScore(new BigDecimal("5.0"))
                .overallBand(new BigDecimal("5.0"))
                .submittedAt(LocalDateTime.now().minusDays(7))
                .build();
    }

    private MockTestSubmission failedInBetween() {
        return MockTestSubmission.builder()
                .submissionId(450L)
                .user(owner)
                .mockTest(mockTest)
                .sessionId(850L)
                .status(SubmissionStatus.FAILED)
                .listeningScore(new BigDecimal("4.0"))
                .readingScore(new BigDecimal("4.0"))
                .writingScore(BigDecimal.ZERO)
                .overallBand(BigDecimal.ZERO)
                .submittedAt(LocalDateTime.now().minusDays(3))
                .build();
    }

    private void stubSession(String progressJson) {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(
                MockTestSession.builder().sessionId(SESSION_ID).progressJson(progressJson).build()));
    }

    // ---------------------------------------------------------------- question analytics

    @Test
    @DisplayName("reading: accuracy per question type is computed and ordered worst first")
    void readingByQuestionType() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        ReadingAnalytics reading = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getReading();

        assertEquals(5, reading.getCorrect());
        assertEquals(9, reading.getTotal());
        assertEquals(55.6, reading.getAccuracy());
        assertEquals(PerformanceLevel.WEAK, reading.getLevel());

        List<String> order = reading.getByQuestionType().stream().map(GroupAccuracy::getKey).collect(Collectors.toList());
        assertEquals(List.of("TFNG", "MCQ", "SHORT_ANSWER"), order);

        GroupAccuracy tfng = reading.getByQuestionType().get(0);
        assertEquals(1, tfng.getCorrect());
        assertEquals(4, tfng.getTotal());
        assertEquals(25.0, tfng.getAccuracy());
        assertEquals(PerformanceLevel.WEAK, tfng.getLevel());
        assertEquals("True / False / Not Given", tfng.getLabel());
        assertEquals(PerformanceLevel.STRONG, reading.getByQuestionType().get(2).getLevel());
    }

    @Test
    @DisplayName("reading: every wrong question is listed with what was answered and what was expected")
    void readingWrongQuestions() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        List<WrongQuestion> wrong = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getReading().getWrongQuestions();

        assertEquals(List.of(102L, 104L, 105L, 106L),
                wrong.stream().map(WrongQuestion::getQuestionId).collect(Collectors.toList()));
        WrongQuestion q104 = wrong.get(1);
        assertEquals("Passage 1", q104.getSection());
        assertEquals("TFNG", q104.getQuestionType());
        assertEquals("TRUE", q104.getUserAnswer());
        assertEquals("FALSE", q104.getCorrectAnswer());
        // Unanswered is reported as such, not as an empty string masquerading as an answer.
        assertNull(wrong.get(2).getUserAnswer());
    }

    @Test
    @DisplayName("listening: accuracy per part keeps exam order; per type is worst first")
    void listeningByPartAndType() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        ListeningAnalytics listening = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getListening();

        assertEquals(4, listening.getCorrect());
        assertEquals(6, listening.getTotal());
        assertEquals(66.7, listening.getAccuracy());
        assertEquals(PerformanceLevel.DEVELOPING, listening.getLevel());

        assertEquals(List.of("Part 1", "Part 2"),
                listening.getByPart().stream().map(GroupAccuracy::getKey).collect(Collectors.toList()));
        assertEquals(PerformanceLevel.STRONG, listening.getByPart().get(0).getLevel());
        assertEquals(33.3, listening.getByPart().get(1).getAccuracy());
        assertEquals(PerformanceLevel.WEAK, listening.getByPart().get(1).getLevel());

        assertEquals(List.of("MCQ", "FILL_BLANK"),
                listening.getByQuestionType().stream().map(GroupAccuracy::getKey).collect(Collectors.toList()));
        assertEquals(2, listening.getWrongQuestions().size());
        assertEquals("Part 2", listening.getWrongQuestions().get(0).getSection());
    }

    @Test
    @DisplayName("writing: the four criteria are combined with Task 2 weighted double")
    void writingCriteria() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        WritingAnalytics writing = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getWriting();

        assertNotNull(writing);
        assertEquals(new BigDecimal("5.5"), writing.getBand());
        assertEquals(PerformanceLevel.DEVELOPING, writing.getLevel());
        assertEquals(701L, writing.getTask1().getSubmissionId());
        assertEquals(new BigDecimal("6.0"), writing.getTask2().getBand());

        Map<String, CriterionScore> combined = writing.getCriteria().stream()
                .collect(Collectors.toMap(CriterionScore::getKey, c -> c));
        // (5.0 + 2 x 6.0) / 3 = 5.67 -> 5.5
        assertEquals(new BigDecimal("5.5"), combined.get("TASK_RESPONSE").getBand());
        // (6.0 + 2 x 7.0) / 3 = 6.67 -> 6.5
        assertEquals(new BigDecimal("6.5"), combined.get("COHERENCE_COHESION").getBand());
        // (6.0 + 2 x 6.5) / 3 = 6.33 -> 6.5
        assertEquals(new BigDecimal("6.5"), combined.get("LEXICAL_RESOURCE").getBand());
        assertEquals(new BigDecimal("5.0"), combined.get("GRAMMAR").getBand());
        assertEquals(PerformanceLevel.WEAK, combined.get("GRAMMAR").getLevel());
        assertEquals(PerformanceLevel.DEVELOPING, combined.get("TASK_RESPONSE").getLevel());
    }

    // ---------------------------------------------------------------- summary & skills

    @Test
    @DisplayName("summary: overall is a 3-skill band, Speaking is declared not included, both tied lowest skills are named")
    void summaryIsHonestAboutScope() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        MockTestAnalyticsResponse response = service.getAnalytics(OWNER_ID, SUBMISSION_ID);
        Summary summary = response.getSummary();

        assertEquals(new BigDecimal("5.5"), summary.getOverallBand());
        assertEquals(new BigDecimal("6.0"), summary.getListeningBand());
        assertFalse(summary.isSpeakingIncluded());
        assertEquals("Speaking: Not included", summary.getSpeakingNote());
        assertTrue(summary.getOverallNote().contains("3-skill"));
        // Reading and Writing both sit at 5.5 under Listening's 6.0.
        assertEquals(List.of(SkillType.READING, SkillType.WRITING), summary.getWeakestSkills());

        Map<SkillType, SkillBreakdown> skills = response.getSkills().stream()
                .collect(Collectors.toMap(SkillBreakdown::getSkill, s -> s));
        assertEquals(new BigDecimal("1.0"), skills.get(SkillType.LISTENING).getGapToTarget());
        assertEquals(new BigDecimal("1.0"), skills.get(SkillType.READING).getGapToTarget());
        // No writing target on the profile falls back to 6.5.
        assertEquals(new BigDecimal("6.5"), skills.get(SkillType.WRITING).getTargetBand());
        assertEquals(4, skills.get(SkillType.LISTENING).getCorrect());
        assertNull(skills.get(SkillType.WRITING).getCorrect());
        assertTrue(skills.get(SkillType.WRITING).isGraded());
    }

    @Test
    @DisplayName("three equal bands name no weakest skill")
    void noWeakestSkillWhenAllEqual() {
        MockTestSubmission sub = completedSubmission();
        sub.setListeningScore(new BigDecimal("5.5"));
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        assertTrue(service.getAnalytics(OWNER_ID, SUBMISSION_ID).getSummary().getWeakestSkills().isEmpty());
    }

    // ---------------------------------------------------------------- weakness detection

    @Test
    @DisplayName("weaknesses: weak before developing, worst first, and groups under the sample size are left out")
    void weaknessDetection() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        List<Weakness> weaknesses = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getWeaknesses();

        List<String> keys = weaknesses.stream().map(Weakness::getKey).collect(Collectors.toList());
        // Reading TFNG 25% < Listening Part 2 33.3% < Writing grammar 5.0 (55.6% of 9).
        assertEquals(List.of("TFNG", "Part 2", "GRAMMAR"), keys.subList(0, 3));
        assertEquals(PerformanceLevel.WEAK, weaknesses.get(2).getLevel());
        assertEquals(PerformanceLevel.DEVELOPING, weaknesses.get(3).getLevel());

        // MCQ was 50% in both skills but only two questions each -- too small to act on.
        assertFalse(keys.contains("MCQ"));
        // SHORT_ANSWER and Part 1 were strong and are not weaknesses.
        assertFalse(keys.contains("SHORT_ANSWER"));
        assertFalse(keys.contains("Part 1"));

        Weakness part2 = weaknesses.get(1);
        assertEquals(SkillType.LISTENING, part2.getSkill());
        assertEquals("PART", part2.getCategory());
        assertEquals(3, part2.getSampleSize());
    }

    // ---------------------------------------------------------------- progress

    @Test
    @DisplayName("progress: completed sittings form the timeline and each skill reports its change")
    void progressTimelineAndTrends() {
        MockTestSubmission current = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(current));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID))
                .thenReturn(List.of(current, failedInBetween(), earlierCompleted()));
        stubSession(ANSWERS);

        Progress progress = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getProgress();

        // The FAILED sitting is not an attempt with a score, so it does not appear.
        assertEquals(2, progress.getTotalAttempts());
        assertEquals(2, progress.getAttemptNumber());
        assertEquals(List.of(400L, SUBMISSION_ID),
                progress.getTimeline().stream().map(TimelinePoint::getSubmissionId).collect(Collectors.toList()));
        assertFalse(progress.getTimeline().get(0).isCurrent());
        assertTrue(progress.getTimeline().get(1).isCurrent());
        assertEquals(new BigDecimal("5.0"), progress.getTimeline().get(0).getListeningBand());

        Map<String, SkillTrend> trends = progress.getTrends().stream()
                .collect(Collectors.toMap(SkillTrend::getSkill, t -> t));
        assertEquals("IMPROVING", trends.get("OVERALL").getDirection());
        assertEquals(new BigDecimal("0.5"), trends.get("OVERALL").getDelta());
        assertEquals(new BigDecimal("1.0"), trends.get("LISTENING").getDelta());
        assertEquals(new BigDecimal("0.5"), trends.get("READING").getDelta());
        assertEquals(new BigDecimal("0.5"), trends.get("WRITING").getDelta());
    }

    @Test
    @DisplayName("progress: a first sitting has nothing to compare against")
    void firstAttemptHasNoDelta() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        Progress progress = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getProgress();

        assertEquals(1, progress.getAttemptNumber());
        assertTrue(progress.getTrends().stream().allMatch(t -> "FIRST_ATTEMPT".equals(t.getDirection())));
        assertTrue(progress.getTrends().stream().allMatch(t -> t.getDelta() == null));
    }

    // ---------------------------------------------------------------- recommendations

    @Test
    @DisplayName("recommendations: at most three, worst weakness first, one per skill, each with a route")
    void recommendations() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession(ANSWERS);

        List<Recommendation> recs = service.getAnalytics(OWNER_ID, SUBMISSION_ID).getRecommendations();

        assertEquals(3, recs.size());
        assertEquals(List.of(SkillType.READING, SkillType.LISTENING, SkillType.WRITING),
                recs.stream().map(Recommendation::getSkill).collect(Collectors.toList()));
        assertEquals("Practise Reading: True / False / Not Given", recs.get(0).getTitle());
        assertEquals("/reading", recs.get(0).getActionPath());
        assertTrue(recs.get(0).getReason().contains("25%"));
        assertEquals("Practise Listening Part 2", recs.get(1).getTitle());
        assertEquals("/listening", recs.get(1).getActionPath());
        assertEquals("Work on Writing: Grammatical Range & Accuracy", recs.get(2).getTitle());
        assertEquals("/writing", recs.get(2).getActionPath());
    }

    @Test
    @DisplayName("recommendations: with nothing weak, the next step is another full test")
    void noWeaknessesRecommendsAnotherTest() {
        MockTestSubmission sub = completedSubmission();
        sub.setWritingTask1Submission(essay(701L, "7.5", "7.5", "7.5", "7.5", "7.5"));
        sub.setWritingTask2Submission(essay(702L, "7.5", "7.5", "7.5", "7.5", "7.5"));
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        // Everything answered correctly.
        stubSession("""
                {"201":"library","202":"centre","203":"B","204":"A","205":"Monday","206":"9am",
                 "101":"A","102":"B","103":"TRUE","104":"FALSE","105":"NOT GIVEN","106":"TRUE",
                 "107":"river","108":"bridge","109":"1990"}
                """);

        MockTestAnalyticsResponse response = service.getAnalytics(OWNER_ID, SUBMISSION_ID);

        assertTrue(response.getWeaknesses().isEmpty());
        assertEquals(1, response.getRecommendations().size());
        assertEquals("/mock-tests", response.getRecommendations().get(0).getActionPath());
        assertNull(response.getRecommendations().get(0).getSkill());
    }

    // ---------------------------------------------------------------- grading in flight

    @Test
    @DisplayName("while Writing is still grading, Listening and Reading analytics are available and nothing is faked")
    void gradingInFlight() {
        MockTestSubmission sub = completedSubmission();
        sub.setStatus(SubmissionStatus.GRADING);
        sub.setWritingScore(BigDecimal.ZERO);
        sub.setOverallBand(BigDecimal.ZERO);
        sub.setWritingTask1Submission(null);
        sub.setWritingTask2Submission(null);
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID))
                .thenReturn(List.of(sub, earlierCompleted()));
        stubSession(ANSWERS);

        MockTestAnalyticsResponse response = service.getAnalytics(OWNER_ID, SUBMISSION_ID);

        assertNull(response.getWriting());
        // The placeholder zeros are not reported as bands.
        assertNull(response.getSummary().getOverallBand());
        assertNull(response.getSummary().getWritingBand());
        assertEquals(new BigDecimal("6.0"), response.getSummary().getListeningBand());
        assertEquals(List.of(SkillType.READING), response.getSummary().getWeakestSkills());

        SkillBreakdown writing = response.getSkills().stream()
                .filter(s -> s.getSkill() == SkillType.WRITING).findFirst().orElseThrow();
        assertFalse(writing.isGraded());
        assertNull(writing.getBand());

        assertEquals(0, response.getProgress().getAttemptNumber());
        assertEquals(1, response.getProgress().getTotalAttempts());
        Map<String, SkillTrend> trends = response.getProgress().getTrends().stream()
                .collect(Collectors.toMap(SkillTrend::getSkill, t -> t));
        assertEquals("PENDING", trends.get("OVERALL").getDirection());
        assertEquals("PENDING", trends.get("WRITING").getDirection());
        // Listening can already be compared with the last completed sitting.
        assertEquals("IMPROVING", trends.get("LISTENING").getDirection());

        assertTrue(response.getWeaknesses().stream().noneMatch(w -> w.getSkill() == SkillType.WRITING));
        assertTrue(response.getRecommendations().stream().noneMatch(r -> r.getSkill() == SkillType.WRITING));
    }

    // ---------------------------------------------------------------- authorization

    @Test
    @DisplayName("another user's submission is refused exactly like a missing one")
    void foreignSubmissionIsNotFound() {
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(completedSubmission()));
        when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException foreign = assertThrows(ResourceNotFoundException.class,
                () -> service.getAnalytics(OTHER_USER_ID, SUBMISSION_ID));
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> service.getAnalytics(OTHER_USER_ID, 999L));

        assertEquals(foreign.getMessage().replace(String.valueOf(SUBMISSION_ID), "X"),
                missing.getMessage().replace("999", "X"));
    }

    @Test
    @DisplayName("an unreadable progress payload counts as unanswered rather than failing the page")
    void corruptProgressJsonIsTolerated() {
        MockTestSubmission sub = completedSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(OWNER_ID)).thenReturn(List.of(sub));
        stubSession("{not json");

        MockTestAnalyticsResponse response = service.getAnalytics(OWNER_ID, SUBMISSION_ID);

        assertEquals(0, response.getReading().getCorrect());
        assertEquals(9, response.getReading().getWrongQuestions().size());
        assertEquals(new BigDecimal("5.5"), response.getSummary().getReadingBand());
    }

    // ---------------------------------------------------------------- fixtures

    private static ListeningPart part(Long id, int number, List<ListeningQuestion> questions) {
        return ListeningPart.builder().partId(id).partNumber(number).title("Part " + number).questions(questions).build();
    }

    private static ListeningQuestion lq(Long id, int order, QuestionType type, String correct) {
        return ListeningQuestion.builder().questionId(id).orderIndex(order).questionType(type)
                .questionText("Q" + order).correctAnswer(correct).build();
    }

    private static ReadingQuiz quiz(Long id, List<ReadingQuestion> questions) {
        return ReadingQuiz.builder().quizId(id).questions(questions).build();
    }

    private static ReadingQuestion rq(Long id, int order, QuestionType type, String correct) {
        return ReadingQuestion.builder().questionId(id).orderIndex(order).questionType(type)
                .questionText("Q" + order).correctAnswer(correct).build();
    }

    private static WritingSubmission essay(Long id, String band, String tr, String cc, String lr, String gra) {
        return WritingSubmission.builder()
                .submissionId(id)
                .wordCount(260)
                .overallBand(new BigDecimal(band))
                .taskResponseScore(new BigDecimal(tr))
                .coherenceScore(new BigDecimal(cc))
                .lexicalScore(new BigDecimal(lr))
                .grammarScore(new BigDecimal(gra))
                .build();
    }
}
