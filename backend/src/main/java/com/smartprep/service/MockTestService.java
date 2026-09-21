package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.MockTestProgressRequest;
import com.smartprep.dto.request.MockTestSubmitRequest;
import com.smartprep.dto.response.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.*;
import com.smartprep.repository.*;
import com.smartprep.config.ExamDurationConfig;
import com.smartprep.service.ai.MockTestAsyncGrader;
import com.smartprep.service.util.IeltsScoringUtils;
import com.smartprep.service.util.QuestionOptionMapper;
import com.smartprep.service.util.UserAnswerSnapshots;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import com.smartprep.service.util.UserPageRequests;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestService {

    private final MockTestRepository mockTestRepository;
    private final MockTestSessionRepository sessionRepository;
    private final MockTestSubmissionRepository submissionRepository;
    private final ListeningTestRepository listeningTestRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final MockTestAsyncGrader asyncGrader;
    private final ExamDurationConfig durationConfig;

    /** Grace window after a section deadline in which a client's answers are still accepted. */
    private static final int SUBMIT_GRACE_SECONDS = 60;

    /**
     * How long a submission may sit in GRADING before a retry is allowed to take it over.
     *
     * <p>Long enough that a genuinely running grade is never stolen — two Gemini calls with a
     * 65-second timeout and three attempts each is a few minutes at worst — and short enough
     * that a candidate whose grading died is not left watching a spinner for the afternoon.
     */
    private static final int STALE_GRADING_MINUTES = 15;

    /**
     * Get all available Mock Tests
     */
    @Transactional(readOnly = true)
    public List<MockTestResponse> getAllMockTests() {
        return mockTestRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Start or resume a Mock Test session
     */
    @Transactional
    public MockTestSessionResponse startOrResumeSession(Long userId, Long mockTestId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Check if there is already an active session for this test/user
        Optional<MockTestSession> activeSessionOpt = sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(
                userId, SessionStatus.IN_PROGRESS
        );

        MockTestSession session;
        if (activeSessionOpt.isPresent()) {
            session = activeSessionOpt.get();
            // If the user wants a different test, we cancel the old one or just resume the current active one
            if (!session.getMockTest().getMockTestId().equals(mockTestId)) {
                session.setStatus(SessionStatus.EXPIRED);
                sessionRepository.save(session);
                session = createNewSession(user, mockTestId);
            } else if (expireIfAbandoned(session)) {
                // Same test, but the previous attempt ran out of time while nobody was
                // looking. It is retired rather than resumed, and the candidate starts again
                // from the beginning instead of inheriting a dead clock.
                session = createNewSession(user, mockTestId);
            }
        } else {
            session = createNewSession(user, mockTestId);
        }

        return mapToSessionResponse(session);
    }

    private MockTestSession createNewSession(User user, Long mockTestId) {
        MockTest mockTest = mockTestRepository.findById(mockTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Mock test not found with id: " + mockTestId));

        int listeningDuration = mockTest.getSections().stream()
                .filter(s -> s.getSectionType() == SkillType.LISTENING)
                .map(MockTestSection::getDurationSeconds)
                .findFirst()
                .orElse(2400);

        MockTestSession newSession = MockTestSession.builder()
                .user(user)
                .mockTest(mockTest)
                .status(SessionStatus.IN_PROGRESS)
                .currentSection(SkillType.LISTENING)
                .timeRemainingSeconds(listeningDuration)
                .progressJson("{}")
                .build();

        return sessionRepository.save(newSession);
    }

    /**
     * Get current active session
     */
    // Not readOnly: reading an abandoned session is what retires it.
    //
    // noRollbackFor is load-bearing, not defensive. This method persists EXPIRED and then
    // throws not-found so the caller sees no active session -- and a RuntimeException
    // leaving a @Transactional method rolls the transaction back, which silently undid the
    // write. The log said "expiring", every subsequent request re-evaluated and re-threw,
    // the UI looked correct, and the row never left IN_PROGRESS. Found in a browser session
    // against a real database; a unit test with a mocked repository cannot see a rollback.
    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public MockTestSessionResponse getActiveSession(Long userId) {
        MockTestSession session = sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(
                userId, SessionStatus.IN_PROGRESS
        ).orElseThrow(() -> new ResourceNotFoundException("No active mock test session found"));

        if (expireIfAbandoned(session)) {
            // It was active a moment ago only in the sense that nothing had looked at it.
            throw new ResourceNotFoundException("No active mock test session found");
        }

        return mapToSessionResponse(session);
    }

    /**
     * Get mock test session by ID
     */
    // Not readOnly: reading an abandoned session is what retires it.
    @Transactional
    public MockTestSessionResponse getSessionById(Long userId, Long sessionId) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }

        // Returned as EXPIRED rather than hidden, so the client can say what happened
        // instead of showing a dead exam with a zeroed clock.
        expireIfAbandoned(session);

        return mapToSessionResponse(session);
    }

    /**
     * Give up on a session the candidate does not want to continue.
     *
     * <p>Until this existed "Abandon Exam" only cleared client state: startOrResumeSession
     * handed the same IN_PROGRESS session straight back on the next Start, and the lobby
     * kept saying "Test in Progress" until the section clock eventually retired it. The
     * session is marked EXPIRED, which is what startOrResumeSession already does to an
     * active session when the candidate picks a different test. Nothing is graded.
     *
     * <p>Idempotent: a session that is already SUBMITTED or EXPIRED is returned as is.
     */
    @Transactional
    public MockTestSessionResponse abandonSession(Long userId, Long sessionId) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }

        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            log.info("Abandoning session {} in {} at the candidate's request",
                    sessionId, session.getCurrentSection());
            session.setStatus(SessionStatus.EXPIRED);
            session.setTimeRemainingSeconds(0);
            session = sessionRepository.save(session);
        }

        return mapToSessionResponse(session);
    }

    /**
     * Save progress (autosave)
     */
    @Transactional
    public MockTestSessionResponse saveProgress(Long userId, Long sessionId, MockTestProgressRequest request) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot update progress on a completed/expired session");
        }

        // Answers come from the client; the clock does not. request.getTimeRemainingSeconds()
        // and request.getCurrentSection() are deliberately ignored — honouring them let a
        // caller grant itself unlimited time or skip ahead simply by posting different values.
        //
        // Answers are also refused once the deadline has passed, and that is not belt and
        // braces: submitExam grades session.progressJson, so an autosave accepted after the
        // bell would be graded on the next submit. Guarding only the submit path left this
        // one wide open -- post the answers here, then submit an empty payload.
        if (isSectionExpired(session)) {
            log.warn("Late autosave for session {}: {} deadline was {}, answers not accepted",
                    sessionId, session.getCurrentSection(), currentSectionDeadline(session));
        } else {
            session.setProgressJson(request.getProgressJson());
        }
        session.setTimeRemainingSeconds(serverTimeRemainingSeconds(session));
        session = sessionRepository.save(session);

        return mapToSessionResponse(session);
    }

    /**
     * Advance to the next section
     */
    @Transactional
    public MockTestSessionResponse nextSection(Long userId, Long sessionId, MockTestProgressRequest request) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        // Advancing after the deadline is refused, and the session is retired.
        //
        // This is the hole that made abandoning an exam profitable. Sections are timed
        // individually and entering one starts its clock, so a candidate who walked away
        // during Listening and came back the next day was moved on to Reading with a full
        // fresh hour. Nothing about leaving the exam cost them anything; the section timer
        // only bound people who stayed. A session that ran out of time on a section it never
        // finished can no longer be continued at all.
        //
        // Submitting is deliberately still allowed past the deadline -- see submitExam. That
        // grades the answers saved in time and confers no advantage, so there is no integrity
        // reason to destroy the candidate's work as well.
        if (isSectionExpired(session)) {
            log.warn("Late section advance for session {}: {} deadline was {}, expiring session",
                    sessionId, session.getCurrentSection(), currentSectionDeadline(session));
            session.setStatus(SessionStatus.EXPIRED);
            session.setTimeRemainingSeconds(0);
            sessionRepository.save(session);
            throw new IllegalStateException(
                    "The time for this section has run out, so the exam can no longer be continued.");
        }

        session.setProgressJson(request.getProgressJson());

        // Transition section
        if (session.getCurrentSection() == SkillType.LISTENING) {
            session.setCurrentSection(SkillType.READING);
            int readingDuration = session.getMockTest().getSections().stream()
                    .filter(s -> s.getSectionType() == SkillType.READING)
                    .map(MockTestSection::getDurationSeconds)
                    .findFirst()
                    .orElse(3600);
            session.setTimeRemainingSeconds(readingDuration);
            session.setSectionStartedAt(LocalDateTime.now());
        } else if (session.getCurrentSection() == SkillType.READING) {
            session.setCurrentSection(SkillType.WRITING);
            int writingDuration = session.getMockTest().getSections().stream()
                    .filter(s -> s.getSectionType() == SkillType.WRITING)
                    .map(MockTestSection::getDurationSeconds)
                    .findFirst()
                    .orElse(3600);
            session.setTimeRemainingSeconds(writingDuration);
            session.setSectionStartedAt(LocalDateTime.now());
        } else {
            throw new IllegalStateException("Already at the final section (Writing). Call submit instead.");
        }

        session = sessionRepository.save(session);
        return mapToSessionResponse(session);
    }

    /**
     * Submit Mock Test and trigger automatic scoring & async essay grading
     */
    @Transactional
    public MockTestSubmissionResponse submitExam(Long userId, Long sessionId, MockTestSubmitRequest request) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Session not found with id: " + sessionId);
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("This exam has already been submitted or expired");
        }

        // A full mock test is only complete after the final section. Without this a caller
        // could submit while still on LISTENING and have the test graded with no Reading or
        // Writing answers at all. The frontend only ever submits from WRITING; earlier
        // sections go through nextSection.
        if (session.getCurrentSection() != SkillType.WRITING) {
            throw new IllegalStateException(
                    "Cannot submit before the final section. Current section: " + session.getCurrentSection());
        }

        // Update session
        // Past the deadline the client's answers are no longer accepted, but the submission
        // itself still succeeds using the last progress saved in time — rejecting it outright
        // would discard work that was done legitimately. The grace window covers auto-submit
        // latency at the moment the timer hits zero.
        boolean lateSubmission = isSectionExpired(session);
        if (lateSubmission) {
            log.warn("Late submit for session {}: deadline was {}, keeping last saved progress",
                    sessionId, currentSectionDeadline(session));
        } else {
            session.setProgressJson(request.getProgressJson());
        }
        session.setStatus(SessionStatus.SUBMITTED);
        session.setTimeRemainingSeconds(0);
        sessionRepository.save(session);

        MockTest mockTest = session.getMockTest();
        User user = session.getUser();

        // Score the progress held on the session, not the request payload.
        //
        // These are the same object on a normal submit, because the branch above has just
        // copied the request onto the session. They differ in exactly one case: a late
        // submission, where the session deliberately keeps the last progress saved before the
        // deadline. Reading the request here scored the late answers anyway, which made the
        // whole late-submission guard decorative -- a caller past the deadline still had its
        // answers graded, it simply was not told so.
        String scoredProgressJson = session.getProgressJson();
        Map<String, String> answersMap = new HashMap<>();
        try {
            answersMap = objectMapper.readValue(scoredProgressJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse progress JSON on submission for session {}", sessionId, e);
        }

        // 1. Calculate Listening Score Instantly
        int listeningCorrect = 0;
        int totalListeningQuestions = 0;
        List<ListeningTestPart> listeningTestParts = new ArrayList<>();
        List<UserAnswer> listeningAnswers = new ArrayList<>();

        ListeningTest listeningTest = ListeningTest.builder()
                .user(user)
                .testMode(TestMode.MOCK_TEST)
                .totalQuestions(0)
                .correctAnswers(0)
                .score(BigDecimal.ZERO)
                .build();

        for (ListeningPart part : mockTest.getListeningParts()) {
            Map<String, String> partAnswersMap = new HashMap<>();
            for (ListeningQuestion q : part.getQuestions()) {
                totalListeningQuestions++;
                String userAnswer = answersMap.get(q.getQuestionId().toString());
                boolean isCorrect = IeltsScoringUtils.isListeningCorrect(q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                if (isCorrect) {
                    listeningCorrect++;
                }
                if (userAnswer != null) {
                    partAnswersMap.put(q.getQuestionId().toString(), userAnswer);
                }
                listeningAnswers.add(UserAnswerSnapshots.forListening(null, totalListeningQuestions, q,
                        userAnswer != null ? userAnswer : "", isCorrect, objectMapper));
            }

            String partAnswersJson = "{}";
            try {
                partAnswersJson = objectMapper.writeValueAsString(partAnswersMap);
            } catch (Exception e) {
                log.error("Failed to serialize part answers", e);
            }

            ListeningTestPart testPart = ListeningTestPart.builder()
                    .test(listeningTest)
                    .part(part)
                    .userAnswersJson(partAnswersJson)
                    .build();
            listeningTestParts.add(testPart);
        }

        BigDecimal listeningBand = IeltsScoringUtils.calculateListeningBand(
                listeningCorrect, totalListeningQuestions);
        listeningTest.setTotalQuestions(totalListeningQuestions);
        listeningTest.setCorrectAnswers(listeningCorrect);
        listeningTest.setScore(listeningBand);
        listeningTest.setTestParts(listeningTestParts);
        listeningTest = listeningTestRepository.save(listeningTest);

        // 2. Calculate Reading Score Instantly
        int readingCorrect = 0;
        int totalReadingQuestions = 0;
        List<UserAnswer> readingAnswers = new ArrayList<>();
        for (ReadingQuiz quiz : mockTest.getReadingQuizzes()) {
            for (ReadingQuestion q : quiz.getQuestions()) {
                totalReadingQuestions++;
                String userAnswer = answersMap.get(q.getQuestionId().toString());
                boolean isCorrect = IeltsScoringUtils.isReadingCorrect(q.getQuestionType(), q.getCorrectAnswer(), userAnswer);
                if (isCorrect) {
                    readingCorrect++;
                }
                readingAnswers.add(UserAnswerSnapshots.forReading(null, totalReadingQuestions, q,
                        userAnswer != null ? userAnswer : "", isCorrect, objectMapper));
            }
        }
        // Use the paper's real module type: Academic and General Training diverge below
        // band 8.0, so assuming Academic silently mis-scored any GT mock test.
        String readingModuleType = mockTest.getReadingQuizzes().stream()
                .map(ReadingQuiz::getModuleType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("ACADEMIC");
        BigDecimal readingBand = IeltsScoringUtils.calculateReadingBand(
                readingCorrect, totalReadingQuestions, readingModuleType);

        // 3. Create placeholder MockTestSubmission record in state GRADING
        MockTestSubmission submission = MockTestSubmission.builder()
                .user(user)
                .mockTest(mockTest)
                .sessionId(sessionId)
                .status(SubmissionStatus.GRADING)
                .listeningScore(listeningBand)
                .readingScore(readingBand)
                .writingScore(BigDecimal.ZERO) // grading pending
                .overallBand(BigDecimal.ZERO)  // grading pending
                .listeningCorrectAnswers(listeningCorrect)
                .readingCorrectAnswers(readingCorrect)
                .listeningTest(listeningTest)
                .build();
        submission = submissionRepository.save(submission);

        // 4. Record the two rule-graded skills the way a practice test does, so the
        // dashboard, score trends, weakness analysis and adaptive difficulty see this
        // sitting. Until now a candidate who only sat full mock tests had an empty
        // dashboard. Writing follows once its asynchronous grade lands, in
        // MockTestGradingPersistence; the link to the submission is what keeps a re-run of
        // that grade from recording the sitting twice.
        recordSkillHistory(user, submission, SkillType.LISTENING, listeningBand, "ACADEMIC", listeningAnswers);
        recordSkillHistory(user, submission, SkillType.READING, readingBand, readingModuleType, readingAnswers);

        // 5. Kick off Asynchronous Writing Evaluation via Gemini
        String task1Essay = answersMap.getOrDefault("w_task1", "");
        String task2Essay = answersMap.getOrDefault("w_task2", "");
        dispatchGradingAfterCommit(submission.getSubmissionId(), task1Essay, task2Essay);

        return MockTestSubmissionResponse.builder()
                .submissionId(submission.getSubmissionId())
                .mockTestId(mockTest.getMockTestId())
                .title(mockTest.getTitle())
                .status(SubmissionStatus.GRADING)
                .listeningScore(listeningBand)
                .readingScore(readingBand)
                .writingScore(BigDecimal.ZERO)
                .overallBand(BigDecimal.ZERO)
                .listeningCorrectAnswers(listeningCorrect)
                .readingCorrectAnswers(readingCorrect)
                .submittedAt(LocalDateTime.now())
                .progressJson(request.getProgressJson())
                .build();
    }

    /**
     * Get Mock Test Submission details
     */
    /**
     * Re-run AI writing evaluation for a submission that never produced a writing band.
     * <p>
     * A transient Gemini outage used to strand a submission in FAILED for good: the
     * listening and reading scores were already computed and saved, but the writing band
     * could never be produced and there was no way to ask for another attempt. The essays
     * are not stored on the submission itself, so they are read back from the session's
     * progress JSON — the same source the original submit used.
     * <p>
     * It also covers the harder case: a submission stuck in GRADING. Grading runs on an async
     * executor, so a process that dies mid-run — a deploy, an OOM kill, a drain that timed
     * out — leaves the row in GRADING with nothing alive to finish it. That state used to be
     * unrecoverable, because retrying required FAILED and nothing ever moved it there. A
     * GRADING submission older than {@link #STALE_GRADING_MINUTES} is treated as abandoned.
     */
    @Transactional
    public void regradeWriting(Long userId, Long submissionId) {
        MockTestSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if (!sub.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }

        // Claim it before doing anything else. The conditional UPDATE decides eligibility and
        // takes ownership in one statement, so two retries racing each other cannot both
        // queue a grading run and produce two sets of essay rows for one sitting.
        int claimed = submissionRepository.claimForRegrade(
                submissionId,
                SubmissionStatus.FAILED,
                SubmissionStatus.GRADING,
                LocalDateTime.now().minusMinutes(STALE_GRADING_MINUTES));

        if (claimed == 0) {
            // Deliberately says nothing about which of those it was: a completed submission,
            // a grading run still in flight, and a retry that lost a race are all "not yours
            // to retry right now".
            throw new IllegalStateException(
                    "This submission is not waiting to be re-graded. Current status: " + sub.getStatus());
        }

        MockTestSession session = sessionRepository.findById(sub.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "The exam session for this submission is no longer available, so the essays cannot be recovered."));

        Map<String, String> answersMap = new HashMap<>();
        try {
            String progressJson = session.getProgressJson();
            if (progressJson != null && !progressJson.trim().isEmpty()) {
                answersMap = objectMapper.readValue(progressJson, new TypeReference<Map<String, String>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to parse progress JSON while re-grading submission {}", submissionId, e);
            throw new IllegalStateException("The stored answers for this exam could not be read.");
        }

        String task1Essay = answersMap.getOrDefault("w_task1", "");
        String task2Essay = answersMap.getOrDefault("w_task2", "");

        // No status write here. The claim above already moved it to GRADING, and it ran with
        // clearAutomatically, so `sub` is a stale detached copy — saving it would merge the
        // pre-claim state back over the row and undo the claim.

        log.info("Re-grading writing for submission {}", submissionId);
        dispatchGradingAfterCommit(submissionId, task1Essay, task2Essay);
    }

    @Transactional(readOnly = true)
    public MockTestSubmissionResponse getSubmission(Long userId, Long submissionId) {
        MockTestSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if (!sub.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches ReviewService.
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }

        MockTestSession session = sessionRepository.findById(sub.getSessionId()).orElse(null);
        String progressJson = session != null ? session.getProgressJson() : "{}";

        Map<String, String> answersMap = new HashMap<>();
        try {
            if (progressJson != null && !progressJson.trim().isEmpty()) {
                answersMap = objectMapper.readValue(progressJson, new TypeReference<Map<String, String>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to parse progress JSON for reading results mapping", e);
        }

        final Map<String, String> finalAnswersMap = answersMap;

        List<ReadingResultResponse> readingResults = sub.getMockTest().getReadingQuizzes().stream()
                .filter(java.util.Objects::nonNull)
                .map(quiz -> {
                    List<ReadingResultResponse.QuestionResultDto> questionResults = quiz.getQuestions().stream()
                            .map(q -> {
                                String userAnswer = finalAnswersMap.get(q.getQuestionId().toString());
                                boolean isCorrect = IeltsScoringUtils.isReadingCorrect(q.getQuestionType(), q.getCorrectAnswer(), userAnswer);
                                return ReadingResultResponse.QuestionResultDto.builder()
                                        .questionId(q.getQuestionId())
                                        .questionType(q.getQuestionType().name())
                                        .questionText(q.getQuestionText())
                                        .options(QuestionOptionMapper.mapForReview(q.getOptions()))
                                        .orderIndex(q.getOrderIndex())
                                        .correctAnswer(q.getCorrectAnswer())
                                        .userAnswer(userAnswer)
                                        .correct(isCorrect)
                                        .explanation(q.getExplanation())
                                        .optionsJson(q.getOptionsJson())
                                        .wordLimit(q.getWordLimit())
                                        .groupLabel(q.getGroupLabel())
                                        .groupId(q.getGroupId())
                                        .groupContext(q.getGroupContext())
                                        .evidenceText(q.getEvidenceText())
                                        .evidenceOffset(q.getEvidenceOffset())
                                        .evidenceLength(q.getEvidenceLength())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    int correctCount = (int) questionResults.stream().filter(ReadingResultResponse.QuestionResultDto::isCorrect).count();

                    return ReadingResultResponse.builder()
                            .quizId(quiz.getQuizId())
                            .topic(quiz.getTopic().name())
                            .difficulty(quiz.getDifficulty().name())
                            .passageText(quiz.getPassageText())
                            .correctAnswers(correctCount)
                            .totalQuestions(questionResults.size())
                            .bandScore(IeltsScoringUtils.calculateReadingBand(
                                    correctCount, questionResults.size(),
                                    quiz.getModuleType() != null ? quiz.getModuleType() : "ACADEMIC"))
                            .questions(questionResults)
                            .build();
                })
                .collect(Collectors.toList());

        WritingGradeResponse w1Response = null;
        if (sub.getWritingTask1Submission() != null) {
            w1Response = mapToWritingGradeResponse(sub.getWritingTask1Submission());
        }

        WritingGradeResponse w2Response = null;
        if (sub.getWritingTask2Submission() != null) {
            w2Response = mapToWritingGradeResponse(sub.getWritingTask2Submission());
        }

        ListeningTestResponse lResponse = null;
        if (sub.getListeningTest() != null) {
            lResponse = mapToListeningTestResponse(sub.getListeningTest());
        }

        return MockTestSubmissionResponse.builder()
                .submissionId(sub.getSubmissionId())
                .mockTestId(sub.getMockTest().getMockTestId())
                .title(sub.getMockTest().getTitle())
                .status(sub.getStatus())
                .listeningScore(sub.getListeningScore())
                .readingScore(sub.getReadingScore())
                .writingScore(sub.getWritingScore())
                .overallBand(sub.getOverallBand())
                .listeningCorrectAnswers(sub.getListeningCorrectAnswers())
                .readingCorrectAnswers(sub.getReadingCorrectAnswers())
                .submittedAt(sub.getSubmittedAt())
                .writingTask1(w1Response)
                .writingTask2(w2Response)
                .listeningTest(lResponse)
                .readingResults(readingResults)
                .progressJson(progressJson)
                .build();
    }

    /**
     * Get user's Mock Test attempt history
     */
    @Transactional(readOnly = true)
    public Page<MockTestHistoryResponse> getHistory(Long userId, int page, int size) {
        return submissionRepository.findByUserUserId(userId,
                        UserPageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt")))
                .map(sub -> MockTestHistoryResponse.builder()
                        .submissionId(sub.getSubmissionId())
                        .mockTestId(sub.getMockTest().getMockTestId())
                        .title(sub.getMockTest().getTitle())
                        .status(sub.getStatus())
                        .overallBand(sub.getOverallBand())
                        .listeningScore(sub.getListeningScore())
                        .readingScore(sub.getReadingScore())
                        .writingScore(sub.getStatus() == SubmissionStatus.COMPLETED ? sub.getWritingScore() : null)
                        .submittedAt(sub.getSubmittedAt())
                        .build());
    }

    /** Mock test sittings are tagged with this in score_history.difficulty. */
    public static final String MOCK_TEST_DIFFICULTY = "MOCK_TEST";

    private void recordSkillHistory(User user, MockTestSubmission submission, SkillType skill,
                                    BigDecimal band, String moduleType, List<UserAnswer> answers) {
        ScoreHistory history = ScoreHistory.builder()
                .user(user)
                .skillType(skill)
                .score(band)
                .difficulty(MOCK_TEST_DIFFICULTY)
                .moduleType(moduleType)
                .mockTestSubmission(submission)
                .build();
        answers.forEach(a -> a.setScoreHistory(history));
        history.setUserAnswers(answers);
        scoreHistoryRepository.save(history);
    }

    // ========== Mapper Methods ==========

    private MockTestResponse mapToResponse(MockTest test) {
        List<ListeningPart> listeningParts = test.getListeningParts();
        List<ReadingQuiz> readingQuizzes = test.getReadingQuizzes();
        List<WritingPrompt> writingPrompts = test.getWritingPrompts();

        List<Long> listeningPartIds = listeningParts == null ? List.of() : listeningParts.stream()
                .filter(java.util.Objects::nonNull)
                .map(ListeningPart::getPartId)
                .collect(Collectors.toList());

        List<Long> readingQuizIds = readingQuizzes == null ? List.of() : readingQuizzes.stream()
                .filter(java.util.Objects::nonNull)
                .map(ReadingQuiz::getQuizId)
                .collect(Collectors.toList());

        List<Long> writingPromptIds = writingPrompts == null ? List.of() : writingPrompts.stream()
                .filter(java.util.Objects::nonNull)
                .map(WritingPrompt::getPromptId)
                .collect(Collectors.toList());

        return MockTestResponse.builder()
                .mockTestId(test.getMockTestId())
                .title(test.getTitle())
                .description(test.getDescription())
                .difficulty(test.getDifficulty())
                .listeningPartsCount(listeningPartIds.size())
                .readingQuizzesCount(readingQuizIds.size())
                .writingPromptsCount(writingPromptIds.size())
                .listeningPartIds(listeningPartIds)
                .readingQuizIds(readingQuizIds)
                .writingPromptIds(writingPromptIds)
                .build();
    }

    /**
     * Queue the async writing grade only once the surrounding transaction has committed.
     *
     * <p>Calling the grader directly from inside {@code submitExam} or {@code regradeWriting}
     * handed the submission id to another thread before the row was committed. That thread
     * opens its own transaction, so under any isolation level above READ UNCOMMITTED its
     * {@code findById} came back empty, it logged "not found", and it aborted -- leaving the
     * submission in GRADING with nothing left to finish it. Whether that happened depended on
     * whether the executor thread or the commit won a race, which is why it was intermittent
     * and why it showed up in a browser session rather than in the unit tests, where the
     * repository is a mock and there is no commit to lose to.
     *
     * <p>Outside a transaction -- the unit tests -- there is nothing to wait for, so the
     * grader is called directly and those tests keep verifying the dispatch.
     */
    private void dispatchGradingAfterCommit(Long submissionId, String task1Essay, String task2Essay) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            asyncGrader.gradeWritingSubmissionsAsync(submissionId, task1Essay, task2Essay);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncGrader.gradeWritingSubmissionsAsync(submissionId, task1Essay, task2Essay);
            }
        });
    }

    /** Configured length of one section, falling back to the IDP standard for that skill. */
    private int sectionDurationSeconds(MockTestSession session, SkillType section) {
        return session.getMockTest().getSections().stream()
                .filter(s -> s.getSectionType() == section)
                .map(MockTestSection::getDurationSeconds)
                .findFirst()
                .orElseGet(() -> durationConfig.getDefaultDuration(section));
    }

    /** When the current section must end. Derived from persisted state, never from the client. */
    private LocalDateTime currentSectionDeadline(MockTestSession session) {
        LocalDateTime sectionStart = session.getSectionStartedAt() != null
                ? session.getSectionStartedAt()
                : session.getStartedAt();
        if (sectionStart == null) {
            // A session that has not been through @PrePersist yet has no start time, so there
            // is no elapsed time to subtract: its section begins now and is owed its full
            // length. Falling through to a null here used to be impossible because only
            // submit and advance consulted the deadline, and both run on a persisted session;
            // the response mapper now consults it too, on a session that may have just been
            // built.
            sectionStart = LocalDateTime.now();
        }
        return sectionStart.plusSeconds(sectionDurationSeconds(session, session.getCurrentSection()));
    }

    /**
     * Seconds left in the current section according to the server clock, floored at zero.
     * <p>
     * This replaces trusting {@code timeRemainingSeconds} as sent by the client, which
     * previously let a caller keep a session alive indefinitely by posting an inflated value.
     */
    private int serverTimeRemainingSeconds(MockTestSession session) {
        long remaining = Duration.between(LocalDateTime.now(), currentSectionDeadline(session)).getSeconds();
        return (int) Math.max(0, remaining);
    }

    /**
     * Retire a session that ran out of time on a section it never finished.
     *
     * <p>Evaluated lazily, on the paths that read a session, so an abandoned exam reaches a
     * settled state without a scheduler. Returns whether it expired.
     *
     * <p>The final section is deliberately exempt. A candidate sitting in Writing when the
     * clock runs out has no further section to gain time on, so expiring them would destroy
     * work rather than protect the exam; their submission is still accepted and graded on the
     * answers saved in time. Everywhere else, running out of time on an unfinished section
     * ends the attempt -- see nextSection for why.
     */
    private boolean expireIfAbandoned(MockTestSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS
                || session.getCurrentSection() == SkillType.WRITING
                || !isSectionExpired(session)) {
            return false;
        }
        log.info("Expiring abandoned session {}: {} deadline was {}",
                session.getSessionId(), session.getCurrentSection(), currentSectionDeadline(session));
        session.setStatus(SessionStatus.EXPIRED);
        session.setTimeRemainingSeconds(0);
        sessionRepository.save(session);
        return true;
    }

    /**
     * True once the current section's deadline has passed, allowing the submit grace period.
     *
     * <p>The single authority on "is this session past its time". It was previously dead code
     * using a 10-second buffer while {@code submitExam} inlined its own 60-second check, so
     * the two disagreed about when a request was late. Everything that cares now asks here.
     */
    private boolean isSectionExpired(MockTestSession session) {
        return LocalDateTime.now().isAfter(
                currentSectionDeadline(session).plusSeconds(SUBMIT_GRACE_SECONDS));
    }

    private MockTestSessionResponse mapToSessionResponse(MockTestSession session) {
        // Recomputed from the deadline every time, not read back from the stored column.
        //
        // timeRemainingSeconds is only written when the client happens to autosave, so on
        // resume the stored value is however much time was left at the last sync. Start
        // Listening, close the tab, come back half an hour later and the server handed back
        // the full 2400 seconds -- the timer was authoritative while you were sitting in the
        // exam and forgiving the moment you left it. A submitted or expired session keeps its
        // stored zero.
        int timeRemaining = session.getStatus() == SessionStatus.IN_PROGRESS
                ? serverTimeRemainingSeconds(session)
                : session.getTimeRemainingSeconds();

        MockTestSessionResponse.MockTestSessionResponseBuilder builder = MockTestSessionResponse.builder()
                .sessionId(session.getSessionId())
                .mockTestId(session.getMockTest().getMockTestId())
                .title(session.getMockTest().getTitle())
                .status(session.getStatus())
                .currentSection(session.getCurrentSection())
                .timeRemainingSeconds(timeRemaining)
                .startedAt(session.getStartedAt())
                .sectionStartedAt(session.getSectionStartedAt())
                .lastSyncedAt(session.getLastSyncedAt())
                .progressJson(session.getProgressJson());

        // Load section details selectively to minimize JSON payload size
        if (session.getCurrentSection() == SkillType.LISTENING) {
            builder.listeningParts(session.getMockTest().getListeningParts().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::mapToListeningPartResponse)
                    .collect(Collectors.toList()));
        } else if (session.getCurrentSection() == SkillType.READING) {
            builder.readingQuizzes(session.getMockTest().getReadingQuizzes().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::mapToReadingQuizResponse)
                    .collect(Collectors.toList()));
        } else if (session.getCurrentSection() == SkillType.WRITING) {
            builder.writingPrompts(session.getMockTest().getWritingPrompts().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::mapToWritingPromptResponse)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private ListeningPartResponse mapToListeningPartResponse(ListeningPart part) {
        return ListeningPartResponse.builder()
                .partId(part.getPartId())
                .partNumber(part.getPartNumber())
                .title(part.getTitle())
                .topic(part.getTopic())
                .audioUrl(part.getAudioUrl())
                .durationSeconds(part.getDurationSeconds())
                .questionCount(part.getQuestions().size())
                .questions(part.getQuestions().stream()
                        .map(q -> ListeningPartResponse.QuestionDto.builder()
                                .questionId(q.getQuestionId())
                                .questionType(q.getQuestionType().name())
                                .questionText(q.getQuestionText())
                                .options(QuestionOptionMapper.mapForExam(q.getOptions()))
                                .orderIndex(q.getOrderIndex())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private ReadingQuizResponse mapToReadingQuizResponse(ReadingQuiz quiz) {
        return ReadingQuizResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .passageText(quiz.getPassageText())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .submitted(false)
                .createdAt(quiz.getCreatedAt())
                .questions(quiz.getQuestions().stream()
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
                        .collect(Collectors.toList()))
                .build();
    }

    private WritingPromptResponse mapToWritingPromptResponse(WritingPrompt prompt) {
        return WritingPromptResponse.builder()
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
                .taskType(prompt.getTaskType() != null ? prompt.getTaskType().name() : null)
                .imageUrl(prompt.getImageUrl())
                .build();
    }

    private WritingGradeResponse mapToWritingGradeResponse(WritingSubmission ws) {
        List<WritingGradeResponse.ErrorDto> errorsList = new ArrayList<>();
        try {
            if (ws.getErrorListJson() != null) {
                errorsList = objectMapper.readValue(ws.getErrorListJson(), new TypeReference<List<WritingGradeResponse.ErrorDto>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to parse errors list from WritingSubmission", e);
        }

        return WritingGradeResponse.builder()
                .submissionId(ws.getSubmissionId())
                .promptId(ws.getPrompt().getPromptId())
                .promptText(ws.getPrompt().getPromptText())
                .essayType(ws.getPrompt().getEssayType().name())
                .essayText(ws.getEssayText())
                .wordCount(ws.getWordCount())
                .overallBand(ws.getOverallBand())
                .taskResponse(ws.getTaskResponseScore())
                .coherence(ws.getCoherenceScore())
                .lexical(ws.getLexicalScore())
                .grammar(ws.getGrammarScore())
                .errors(errorsList)
                .generalFeedback(ws.getAiFeedback())
                .rewrittenVersion(ws.getRewrittenVersion())
                .submittedAt(ws.getSubmittedAt())
                .build();
    }

    private ListeningTestResponse mapToListeningTestResponse(ListeningTest lt) {
        return ListeningTestResponse.builder()
                .testId(lt.getTestId())
                .score(lt.getScore())
                .totalQuestions(lt.getTotalQuestions())
                .correctAnswers(lt.getCorrectAnswers())
                .submittedAt(lt.getSubmittedAt())
                .build();
    }
}
