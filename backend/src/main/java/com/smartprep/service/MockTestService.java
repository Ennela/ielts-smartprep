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
import com.smartprep.service.ai.MockTestAsyncGrader;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestService {

    private final MockTestRepository mockTestRepository;
    private final MockTestSessionRepository sessionRepository;
    private final MockTestSubmissionRepository submissionRepository;
    private final ListeningTestRepository listeningTestRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final MockTestAsyncGrader asyncGrader;

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
    @Transactional(readOnly = true)
    public MockTestSessionResponse getActiveSession(Long userId) {
        MockTestSession session = sessionRepository.findFirstByUserUserIdAndStatusOrderByStartedAtDesc(
                userId, SessionStatus.IN_PROGRESS
        ).orElseThrow(() -> new ResourceNotFoundException("No active mock test session found"));

        return mapToSessionResponse(session);
    }

    /**
     * Get mock test session by ID
     */
    @Transactional(readOnly = true)
    public MockTestSessionResponse getSessionById(Long userId, Long sessionId) {
        MockTestSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to this session");
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
            throw new SecurityException("Unauthorized access to this session");
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot update progress on a completed/expired session");
        }

        session.setProgressJson(request.getProgressJson());
        session.setTimeRemainingSeconds(request.getTimeRemainingSeconds());
        session.setCurrentSection(request.getCurrentSection());
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
            throw new SecurityException("Unauthorized access to this session");
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        // Save progress first
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
            throw new SecurityException("Unauthorized access to this session");
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("This exam has already been submitted or expired");
        }

        // Update session
        session.setProgressJson(request.getProgressJson());
        session.setStatus(SessionStatus.SUBMITTED);
        session.setTimeRemainingSeconds(0);
        sessionRepository.save(session);

        MockTest mockTest = session.getMockTest();
        User user = session.getUser();

        // Parse answers
        Map<String, String> answersMap = new HashMap<>();
        try {
            answersMap = objectMapper.readValue(request.getProgressJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse progress JSON on submission: {}", request.getProgressJson(), e);
        }

        // 1. Calculate Listening Score Instantly
        int listeningCorrect = 0;
        int totalListeningQuestions = 0;
        List<ListeningTestPart> listeningTestParts = new ArrayList<>();

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

        BigDecimal listeningBand = IeltsScoringUtils.calculateListeningBand(listeningCorrect);
        listeningTest.setTotalQuestions(totalListeningQuestions);
        listeningTest.setCorrectAnswers(listeningCorrect);
        listeningTest.setScore(listeningBand);
        listeningTest.setTestParts(listeningTestParts);
        listeningTest = listeningTestRepository.save(listeningTest);

        // 2. Calculate Reading Score Instantly
        int readingCorrect = 0;
        int totalReadingQuestions = 0;
        for (ReadingQuiz quiz : mockTest.getReadingQuizzes()) {
            for (ReadingQuestion q : quiz.getQuestions()) {
                totalReadingQuestions++;
                String userAnswer = answersMap.get(q.getQuestionId().toString());
                boolean isCorrect = IeltsScoringUtils.isReadingCorrect(q.getQuestionType(), q.getCorrectAnswer(), userAnswer);
                if (isCorrect) {
                    readingCorrect++;
                }
            }
        }
        BigDecimal readingBand = IeltsScoringUtils.calculateReadingBand(readingCorrect);

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

        // 4. Kick off Asynchronous Writing Evaluation via Gemini
        String task1Essay = answersMap.getOrDefault("w_task1", "");
        String task2Essay = answersMap.getOrDefault("w_task2", "");
        asyncGrader.gradeWritingSubmissionsAsync(submission.getSubmissionId(), task1Essay, task2Essay);

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
    @Transactional(readOnly = true)
    public MockTestSubmissionResponse getSubmission(Long userId, Long submissionId) {
        MockTestSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if (!sub.getUser().getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to this report");
        }

        MockTestSession session = sessionRepository.findById(sub.getSessionId()).orElse(null);
        String progressJson = session != null ? session.getProgressJson() : "{}";

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
                .progressJson(progressJson)
                .build();
    }

    /**
     * Get user's Mock Test attempt history
     */
    @Transactional(readOnly = true)
    public List<MockTestHistoryResponse> getHistory(Long userId) {
        return submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(sub -> MockTestHistoryResponse.builder()
                        .submissionId(sub.getSubmissionId())
                        .mockTestId(sub.getMockTest().getMockTestId())
                        .title(sub.getMockTest().getTitle())
                        .status(sub.getStatus())
                        .overallBand(sub.getOverallBand())
                        .submittedAt(sub.getSubmittedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ========== Mapper Methods ==========

    private MockTestResponse mapToResponse(MockTest test) {
        return MockTestResponse.builder()
                .mockTestId(test.getMockTestId())
                .title(test.getTitle())
                .description(test.getDescription())
                .difficulty(test.getDifficulty())
                .listeningPartsCount(test.getListeningParts().size())
                .readingQuizzesCount(test.getReadingQuizzes().size())
                .writingPromptsCount(test.getWritingPrompts().size())
                .listeningPartIds(test.getListeningParts().stream().map(ListeningPart::getPartId).collect(Collectors.toList()))
                .readingQuizIds(test.getReadingQuizzes().stream().map(ReadingQuiz::getQuizId).collect(Collectors.toList()))
                .writingPromptIds(test.getWritingPrompts().stream().map(WritingPrompt::getPromptId).collect(Collectors.toList()))
                .build();
    }

    private MockTestSessionResponse mapToSessionResponse(MockTestSession session) {
        MockTestSessionResponse.MockTestSessionResponseBuilder builder = MockTestSessionResponse.builder()
                .sessionId(session.getSessionId())
                .mockTestId(session.getMockTest().getMockTestId())
                .title(session.getMockTest().getTitle())
                .status(session.getStatus())
                .currentSection(session.getCurrentSection())
                .timeRemainingSeconds(session.getTimeRemainingSeconds())
                .startedAt(session.getStartedAt())
                .sectionStartedAt(session.getSectionStartedAt())
                .lastSyncedAt(session.getLastSyncedAt())
                .progressJson(session.getProgressJson());

        // Load section details selectively to minimize JSON payload size
        if (session.getCurrentSection() == SkillType.LISTENING) {
            builder.listeningParts(session.getMockTest().getListeningParts().stream()
                    .map(this::mapToListeningPartResponse)
                    .collect(Collectors.toList()));
        } else if (session.getCurrentSection() == SkillType.READING) {
            builder.readingQuizzes(session.getMockTest().getReadingQuizzes().stream()
                    .map(this::mapToReadingQuizResponse)
                    .collect(Collectors.toList()));
        } else if (session.getCurrentSection() == SkillType.WRITING) {
            builder.writingPrompts(session.getMockTest().getWritingPrompts().stream()
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
                                .options(mapOptions(q.getOptions(), false))
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
                                .options(mapOptions(q.getOptions(), false))
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

    private List<QuestionOptionResponse> mapOptions(List<QuestionOption> options, boolean showCorrect) {
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

    private WritingPromptResponse mapToWritingPromptResponse(WritingPrompt prompt) {
        return WritingPromptResponse.builder()
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
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
