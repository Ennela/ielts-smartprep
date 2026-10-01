package com.smartprep.service;

import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A learner sits a copy of a template. Every field that decides how a question is marked
 * has to survive the copy: a Choose-TWO task copied without its select_count would be
 * marked as two single-answer questions, and the learner's choice "B,D" would score nothing.
 */
@ExtendWith(MockitoExtension.class)
class ReadingTemplateCopyKeepsSelectCountTest {

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReadingQueryService readingQueryService;

    @InjectMocks private ReadingAssemblyService readingAssemblyService;

    @Test
    @DisplayName("the learner's copy keeps select_count")
    void copyKeepsSelectCount() {
        ReadingQuiz template = ReadingQuiz.builder()
                .quizId(9L).isTemplate(true).difficulty(Difficulty.PASSAGE_1).passageText("Text")
                .questions(new ArrayList<>()).build();
        for (String letter : List.of("B", "D")) {
            template.getQuestions().add(ReadingQuestion.builder()
                    .quiz(template).questionType(QuestionType.MCQ).questionText("Which TWO?")
                    .correctAnswer(letter).orderIndex(template.getQuestions().size() + 1)
                    .groupId(4).selectCount(2).build());
        }
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).build()));
        when(quizRepository.findById(9L)).thenReturn(Optional.of(template));
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(inv -> inv.getArgument(0));

        readingAssemblyService.startTemplateQuiz(1L, 9L);

        ArgumentCaptor<ReadingQuiz> copy = ArgumentCaptor.forClass(ReadingQuiz.class);
        verify(quizRepository).save(copy.capture());
        assertThat(copy.getValue().getQuestions()).extracting(ReadingQuestion::getSelectCount).containsExactly(2, 2);
    }
}
