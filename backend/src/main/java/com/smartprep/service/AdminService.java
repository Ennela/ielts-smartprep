package com.smartprep.service;

import com.smartprep.dto.request.AdminMockTestRequest;
import com.smartprep.dto.request.AdminReadingQuizRequest;
import com.smartprep.dto.request.AdminWritingPromptRequest;
import com.smartprep.dto.response.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Topic;
import com.smartprep.model.enums.EssayType;
import com.smartprep.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final WritingPromptRepository writingPromptRepository;
    private final ReadingQuizRepository readingQuizRepository;
    private final MockTestRepository mockTestRepository;
    private final ListeningPartRepository listeningPartRepository;

    /**
     * List users with pagination and optional search.
     */
    public Page<AdminUserResponse> listUsers(String search, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage;
        if (search != null && !search.isBlank()) {
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    search, search, pageRequest);
        } else {
            userPage = userRepository.findAll(pageRequest);
        }

        return userPage.map(this::toAdminUserResponse);
    }

    /**
     * Get detailed user info with per-skill stats and recent scores.
     */
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Per-skill averages
        List<Object[]> skillAverages = scoreHistoryRepository.getSkillAverages(userId);
        List<AdminUserDetailResponse.SkillStat> skillStats = new ArrayList<>();
        for (Object[] row : skillAverages) {
            skillStats.add(AdminUserDetailResponse.SkillStat.builder()
                    .skill(row[0].toString())
                    .avgScore(BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP))
                    .totalTests(((Number) row[2]).longValue())
                    .build());
        }

        // Recent 10 scores
        Page<ScoreHistory> recentPage = scoreHistoryRepository.findByUserUserIdOrderByRecordedAtDesc(
                userId, PageRequest.of(0, 10));
        List<AdminUserDetailResponse.RecentScore> recentScores = recentPage.getContent().stream()
                .map(sh -> AdminUserDetailResponse.RecentScore.builder()
                        .skillType(sh.getSkillType().name())
                        .score(sh.getScore())
                        .recordedAt(sh.getRecordedAt())
                        .build())
                .toList();

        return AdminUserDetailResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole().name() : "STUDENT")
                .targetReadingScore(user.getTargetReadingScore())
                .targetWritingScore(user.getTargetWritingScore())
                .targetListeningScore(user.getTargetListeningScore())
                .createdAt(user.getCreatedAt())
                .skillStats(skillStats)
                .recentScores(recentScores)
                .build();
    }

    // ===== Dashboard Stats =====

    public AdminDashboardResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long testsToday = scoreHistoryRepository.countByRecordedAtAfter(todayStart);
        // pendingWritings: count submissions not yet graded (overallBand is null) — simplified as 0 for now
        long pendingWritings = 0;
        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .testsToday(testsToday)
                .pendingWritings(pendingWritings)
                .apiHealthy(true)
                .build();
    }

    // ===== Writing Prompts CRUD =====

    public Page<WritingPrompt> listWritingPrompts(String essayType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (essayType != null && !essayType.isBlank()) {
            EssayType type = EssayType.valueOf(essayType.toUpperCase());
            return writingPromptRepository.findByEssayTypeOrderByCreatedAtDesc(type, pageRequest);
        }
        return writingPromptRepository.findAllByOrderByCreatedAtDesc(pageRequest);
    }

    public WritingPrompt createWritingPrompt(AdminWritingPromptRequest request) {
        WritingPrompt prompt = WritingPrompt.builder()
                .promptText(request.getPromptText())
                .essayType(EssayType.valueOf(request.getEssayType().toUpperCase()))
                .imageUrl(request.getImageUrl())
                .build();
        return writingPromptRepository.save(prompt);
    }

    public WritingPrompt updateWritingPrompt(Long promptId, AdminWritingPromptRequest request) {
        WritingPrompt prompt = writingPromptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found: " + promptId));
        prompt.setPromptText(request.getPromptText());
        prompt.setEssayType(EssayType.valueOf(request.getEssayType().toUpperCase()));
        prompt.setImageUrl(request.getImageUrl());
        return writingPromptRepository.save(prompt);
    }

    public void deleteWritingPrompt(Long promptId) {
        if (!writingPromptRepository.existsById(promptId)) {
            throw new ResourceNotFoundException("Writing prompt not found: " + promptId);
        }
        writingPromptRepository.deleteById(promptId);
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        List<Object[]> avgs = scoreHistoryRepository.getSkillAverages(user.getUserId());
        long totalTests = 0;
        double totalScore = 0;
        int count = 0;
        for (Object[] row : avgs) {
            totalTests += ((Number) row[2]).longValue();
            totalScore += ((Number) row[1]).doubleValue();
            count++;
        }
        BigDecimal avgScore = count > 0
                ? BigDecimal.valueOf(totalScore / count).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole() != null ? user.getRole().name() : "STUDENT")
                .targetReadingScore(user.getTargetReadingScore())
                .targetWritingScore(user.getTargetWritingScore())
                .targetListeningScore(user.getTargetListeningScore())
                .totalTests(totalTests)
                .avgScore(avgScore)
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ===== Reading Quizzes CRUD =====

    public Page<AdminReadingQuizResponse> listReadingQuizzes(String topicStr, String difficultyStr, String source, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Topic topic = (topicStr != null && !topicStr.isBlank()) ? Topic.valueOf(topicStr.toUpperCase()) : null;
        Difficulty difficulty = (difficultyStr != null && !difficultyStr.isBlank()) ? Difficulty.valueOf(difficultyStr.toUpperCase()) : null;
        String cleanSource = (source != null && !source.isBlank()) ? source.toUpperCase() : null;
        return readingQuizRepository.findQuizzesForAdmin(topic, difficulty, cleanSource, pageRequest)
                .map(this::toAdminReadingQuizResponse);
    }

    @Transactional
    public AdminReadingQuizResponse createReadingQuiz(AdminReadingQuizRequest request) {
        Topic topic = Topic.valueOf(request.getTopic().toUpperCase());
        Difficulty difficulty = Difficulty.valueOf(request.getDifficulty().toUpperCase());

        ReadingQuiz quiz = ReadingQuiz.builder()
                .topic(topic)
                .difficulty(difficulty)
                .passageText(request.getPassageText())
                .timeLimitSeconds(request.getTimeLimitSeconds())
                .totalQuestions(request.getQuestions().size())
                .isTemplate(true)
                .build();

        List<ReadingQuestion> questions = request.getQuestions().stream()
                .map(q -> ReadingQuestion.builder()
                        .quiz(quiz)
                        .questionType(QuestionType.valueOf(q.getQuestionType().toUpperCase()))
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .correctAnswer(q.getCorrectAnswer().trim())
                        .explanation(q.getExplanation())
                        .orderIndex(q.getOrderIndex())
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        quiz.setQuestions(questions);
        ReadingQuiz saved = readingQuizRepository.save(quiz);
        return toAdminReadingQuizResponse(saved);
    }

    @Transactional
    public AdminReadingQuizResponse updateReadingQuiz(Long quizId, AdminReadingQuizRequest request) {
        ReadingQuiz quiz = readingQuizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Reading quiz not found: " + quizId));

        quiz.setTopic(Topic.valueOf(request.getTopic().toUpperCase()));
        quiz.setDifficulty(Difficulty.valueOf(request.getDifficulty().toUpperCase()));
        quiz.setPassageText(request.getPassageText());
        quiz.setTimeLimitSeconds(request.getTimeLimitSeconds());
        quiz.setTotalQuestions(request.getQuestions().size());

        // Clear and reload questions (supported by orphanRemoval=true)
        quiz.getQuestions().clear();

        List<ReadingQuestion> questions = request.getQuestions().stream()
                .map(q -> ReadingQuestion.builder()
                        .quiz(quiz)
                        .questionType(QuestionType.valueOf(q.getQuestionType().toUpperCase()))
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .correctAnswer(q.getCorrectAnswer().trim())
                        .explanation(q.getExplanation())
                        .orderIndex(q.getOrderIndex())
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        quiz.getQuestions().addAll(questions);

        ReadingQuiz saved = readingQuizRepository.save(quiz);
        return toAdminReadingQuizResponse(saved);
    }

    @Transactional
    public void deleteReadingQuiz(Long quizId) {
        if (!readingQuizRepository.existsById(quizId)) {
            throw new ResourceNotFoundException("Reading quiz not found: " + quizId);
        }
        readingQuizRepository.deleteById(quizId);
    }

    public AdminReadingQuizResponse toAdminReadingQuizResponse(ReadingQuiz quiz) {
        List<AdminReadingQuizResponse.QuestionDto> questionDtos = quiz.getQuestions().stream()
                .map(q -> AdminReadingQuizResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .correctAnswer(q.getCorrectAnswer())
                        .explanation(q.getExplanation())
                        .orderIndex(q.getOrderIndex())
                        .optionsJson(q.getOptionsJson())
                        .wordLimit(q.getWordLimit())
                        .groupLabel(q.getGroupLabel())
                        .groupId(q.getGroupId())
                        .groupContext(q.getGroupContext())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        return AdminReadingQuizResponse.builder()
                .quizId(quiz.getQuizId())
                .topic(quiz.getTopic().name())
                .difficulty(quiz.getDifficulty().name())
                .passageText(quiz.getPassageText())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .totalQuestions(quiz.getTotalQuestions())
                .createdAt(quiz.getCreatedAt())
                .questions(questionDtos)
                .isTemplate(quiz.getIsTemplate())
                .createdBy(Boolean.TRUE.equals(quiz.getIsTemplate()) ? "Admin" : (quiz.getUser() != null ? quiz.getUser().getUsername() : "AI"))
                .build();
    }

    // ===== Mock Tests CRUD =====

    public Page<MockTestResponse> listMockTests(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return mockTestRepository.findAll(pageRequest).map(this::mapToMockTestResponse);
    }

    @Transactional
    public MockTestResponse createMockTest(AdminMockTestRequest request) {
        List<ListeningPart> listeningParts = new ArrayList<>();
        for (Long id : request.getListeningPartIds()) {
            listeningPartRepository.findById(id).ifPresent(listeningParts::add);
        }

        List<ReadingQuiz> readingQuizzes = new ArrayList<>();
        for (Long id : request.getReadingQuizIds()) {
            readingQuizRepository.findById(id).ifPresent(readingQuizzes::add);
        }

        List<WritingPrompt> writingPrompts = new ArrayList<>();
        for (Long id : request.getWritingPromptIds()) {
            writingPromptRepository.findById(id).ifPresent(writingPrompts::add);
        }

        MockTest mockTest = MockTest.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .difficulty(Difficulty.valueOf(request.getDifficulty().toUpperCase()))
                .listeningParts(listeningParts)
                .readingQuizzes(readingQuizzes)
                .writingPrompts(writingPrompts)
                .build();

        MockTest saved = mockTestRepository.save(mockTest);
        return mapToMockTestResponse(saved);
    }

    @Transactional
    public MockTestResponse updateMockTest(Long mockTestId, AdminMockTestRequest request) {
        MockTest mockTest = mockTestRepository.findById(mockTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Mock test not found: " + mockTestId));

        List<ListeningPart> listeningParts = new ArrayList<>();
        for (Long id : request.getListeningPartIds()) {
            listeningPartRepository.findById(id).ifPresent(listeningParts::add);
        }

        List<ReadingQuiz> readingQuizzes = new ArrayList<>();
        for (Long id : request.getReadingQuizIds()) {
            readingQuizRepository.findById(id).ifPresent(readingQuizzes::add);
        }

        List<WritingPrompt> writingPrompts = new ArrayList<>();
        for (Long id : request.getWritingPromptIds()) {
            writingPromptRepository.findById(id).ifPresent(writingPrompts::add);
        }

        mockTest.setTitle(request.getTitle());
        mockTest.setDescription(request.getDescription());
        mockTest.setDifficulty(Difficulty.valueOf(request.getDifficulty().toUpperCase()));
        mockTest.setListeningParts(listeningParts);
        mockTest.setReadingQuizzes(readingQuizzes);
        mockTest.setWritingPrompts(writingPrompts);

        MockTest saved = mockTestRepository.save(mockTest);
        return mapToMockTestResponse(saved);
    }

    @Transactional
    public void deleteMockTest(Long mockTestId) {
        if (!mockTestRepository.existsById(mockTestId)) {
            throw new ResourceNotFoundException("Mock test not found: " + mockTestId);
        }
        mockTestRepository.deleteById(mockTestId);
    }

    private MockTestResponse mapToMockTestResponse(MockTest test) {
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
}
