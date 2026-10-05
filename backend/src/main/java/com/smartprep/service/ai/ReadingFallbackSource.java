package com.smartprep.service.ai;

import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The stored quiz Reading generation falls back to when Gemini fails, copied for the learner.
 *
 * <p>A bean of its own so the copy runs in a short read-only transaction: the template's
 * questions and options are lazy, and with open-in-view off nothing else keeps a session
 * open for them. The caller saves the copy; the generation service itself stays
 * non-transactional, so no connection is held across a Gemini call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingFallbackSource {

    private final ReadingQuizRepository quizRepository;

    /** An unsaved copy of a random stored quiz for this topic and difficulty, widening the search if none. */
    @Transactional(readOnly = true)
    public ReadingQuiz copyTemplate(User user, Topic topic, Difficulty difficulty, String moduleType,
                                    Exception originalException) {
        List<ReadingQuiz> templates = quizRepository.findQuizzesForAdmin(topic, difficulty, null, PageRequest.of(0, 10)).getContent();
        if (templates.isEmpty()) {
            templates = quizRepository.findQuizzesForAdmin(null, difficulty, null, PageRequest.of(0, 10)).getContent();
        }
        if (templates.isEmpty()) {
            templates = quizRepository.findAll(PageRequest.of(0, 10)).getContent();
        }
        if (templates.isEmpty()) {
            log.error("No fallback quizzes found in the database. Failing request.");
            if (originalException instanceof RuntimeException) {
                throw (RuntimeException) originalException;
            }
            throw new RuntimeException("AI Content generation failed and no fallback content was available in the system.", originalException);
        }

        ReadingQuiz selected = templates.get(new Random().nextInt(templates.size()));
        log.info("Selected fallback ReadingQuiz ID: {} for user: {}", selected.getQuizId(), user.getUserId());

        ReadingQuiz fallbackQuiz = ReadingQuiz.builder()
                .user(user)
                .topic(selected.getTopic())
                .difficulty(selected.getDifficulty())
                .moduleType(moduleType)
                .passageText(selected.getPassageText())
                .timeLimitSeconds(selected.getTimeLimitSeconds() != null ? selected.getTimeLimitSeconds() : ReadingGenerationService.TIME_LIMITS.get(selected.getDifficulty()))
                .totalQuestions(selected.getTotalQuestions())
                .isTemplate(false)
                .parentTemplateId(selected.getQuizId())
                .build();

        List<ReadingQuestion> clonedQuestions = new ArrayList<>();
        for (ReadingQuestion q : selected.getQuestions()) {
            ReadingQuestion clonedQ = ReadingQuestion.builder()
                    .quiz(fallbackQuiz)
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .correctAnswer(q.getCorrectAnswer())
                    .explanation(q.getExplanation())
                    .orderIndex(q.getOrderIndex())
                    .groupLabel(q.getGroupLabel())
                    .selectCount(q.getSelectCount())
                    .imageUrl(q.getImageUrl())
                    .groupId(q.getGroupId())
                    .groupContext(q.getGroupContext())
                    .wordLimit(q.getWordLimit())
                    .optionsJson(q.getOptionsJson())
                    .evidenceText(q.getEvidenceText())
                    .evidenceOffset(q.getEvidenceOffset())
                    .evidenceLength(q.getEvidenceLength())
                    .build();

            if (q.getOptions() != null) {
                List<QuestionOption> clonedOptions = new ArrayList<>();
                for (QuestionOption opt : q.getOptions()) {
                    clonedOptions.add(QuestionOption.builder()
                            .readingQuestion(clonedQ)
                            .label(opt.getLabel())
                            .content(opt.getContent())
                            .isCorrect(opt.getIsCorrect())
                            .orderIndex(opt.getOrderIndex())
                            .build());
                }
                clonedQ.setOptions(clonedOptions);
            }
            clonedQuestions.add(clonedQ);
        }
        fallbackQuiz.setQuestions(clonedQuestions);
        return fallbackQuiz;
    }
}
