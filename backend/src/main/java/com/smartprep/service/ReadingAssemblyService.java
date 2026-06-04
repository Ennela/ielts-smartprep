package com.smartprep.service;

import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles full Reading mock tests and handles template-based quiz starts.
 * Extracted from the original ReadingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingAssemblyService {

    private final ReadingQuizRepository quizRepository;
    private final UserRepository userRepository;
    private final ReadingQueryService readingQueryService;

    @Transactional
    public ReadingQuizResponse startTemplateQuiz(Long userId, Long templateId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ReadingQuiz template = quizRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Reading template not found: " + templateId));
        if (!template.getIsTemplate()) {
            throw new IllegalArgumentException("Target quiz is not a template");
        }
        return cloneTemplateForUser(user, template);
    }

    @Transactional
    public List<ReadingQuizResponse> assembleMockTest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ReadingQuizResponse> quizzes = new ArrayList<>();
        Difficulty[] difficulties = {Difficulty.PASSAGE_1, Difficulty.PASSAGE_2, Difficulty.PASSAGE_3};

        for (Difficulty difficulty : difficulties) {
            var templatePage = quizRepository.findQuizzesForAdmin(null, difficulty, "ADMIN", PageRequest.of(0, 100));
            List<ReadingQuiz> candidates = new ArrayList<>(templatePage.getContent());

            if (candidates.isEmpty()) {
                var aiPage = quizRepository.findQuizzesForAdmin(null, difficulty, "AI", PageRequest.of(0, 100));
                candidates.addAll(aiPage.getContent());
            }
            if (candidates.isEmpty()) {
                throw new ResourceNotFoundException("No reading quizzes available for difficulty " + difficulty.name() + ". Please generate one first.");
            }

            ReadingQuiz template = candidates.get(new java.util.Random().nextInt(candidates.size()));
            quizzes.add(cloneTemplateForUser(user, template));
        }
        return quizzes;
    }

    private ReadingQuizResponse cloneTemplateForUser(User user, ReadingQuiz template) {
        ReadingQuiz userQuiz = ReadingQuiz.builder()
                .user(user).topic(template.getTopic()).difficulty(template.getDifficulty())
                .passageText(template.getPassageText()).timeLimitSeconds(template.getTimeLimitSeconds())
                .totalQuestions(template.getTotalQuestions()).isTemplate(false)
                .parentTemplateId(template.getQuizId()).build();

        List<ReadingQuestion> questions = template.getQuestions().stream()
                .map(q -> {
                    ReadingQuestion question = ReadingQuestion.builder()
                            .quiz(userQuiz).questionType(q.getQuestionType())
                            .questionText(q.getQuestionText()).correctAnswer(q.getCorrectAnswer())
                            .explanation(q.getExplanation()).orderIndex(q.getOrderIndex())
                            .optionsJson(q.getOptionsJson()).wordLimit(q.getWordLimit())
                            .groupLabel(q.getGroupLabel()).groupId(q.getGroupId())
                            .groupContext(q.getGroupContext()).build();

                    List<QuestionOption> options = new ArrayList<>();
                    if (q.getOptions() != null) {
                        for (QuestionOption opt : q.getOptions()) {
                            options.add(QuestionOption.builder()
                                    .readingQuestion(question).label(opt.getLabel())
                                    .content(opt.getContent()).isCorrect(opt.getIsCorrect())
                                    .orderIndex(opt.getOrderIndex()).build());
                        }
                    }
                    question.setOptions(options);
                    return question;
                }).collect(Collectors.toList());

        userQuiz.setQuestions(questions);
        ReadingQuiz saved = quizRepository.save(userQuiz);
        return readingQueryService.mapToQuizResponse(saved);
    }
}
