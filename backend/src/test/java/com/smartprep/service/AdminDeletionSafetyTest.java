package com.smartprep.service;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSection;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDeletionSafetyTest {

    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private WritingPromptRepository writingPromptRepository;
    @Mock private ReadingQuizRepository readingQuizRepository;
    @Mock private MockTestRepository mockTestRepository;
    @Mock private ListeningPartRepository listeningPartRepository;
    @Mock private AudioGenerationService audioGenerationService;

    @InjectMocks private AdminService adminService;
    @InjectMocks private AdminListeningService adminListeningService;

    @Test
    void deleteReadingQuizArchivesWithoutDeletingQuestions() {
        List<ReadingQuestion> questions = new ArrayList<>(List.of(ReadingQuestion.builder().build()));
        ReadingQuiz quiz = ReadingQuiz.builder().quizId(41L).questions(questions).build();
        when(readingQuizRepository.findById(41L)).thenReturn(Optional.of(quiz));

        adminService.deleteReadingQuiz(41L);

        assertNotNull(quiz.getDeletedAt());
        assertSame(questions, quiz.getQuestions());
        verify(readingQuizRepository).save(quiz);
        verify(readingQuizRepository, never()).deleteById(41L);
        verify(readingQuizRepository, never()).delete(quiz);
    }

    @Test
    void deleteWritingPromptArchivesWithoutPhysicalDelete() {
        WritingPrompt prompt = WritingPrompt.builder().promptId(42L).build();
        when(writingPromptRepository.findById(42L)).thenReturn(Optional.of(prompt));

        adminService.deleteWritingPrompt(42L);

        assertNotNull(prompt.getDeletedAt());
        verify(writingPromptRepository).save(prompt);
        verify(writingPromptRepository, never()).deleteById(42L);
        verify(writingPromptRepository, never()).delete(prompt);
    }

    @Test
    void deleteMockTestArchivesWithoutRemovingSharedContent() {
        ReadingQuiz quiz = ReadingQuiz.builder().quizId(43L).build();
        ListeningPart part = ListeningPart.builder().partId(44L).build();
        WritingPrompt prompt = WritingPrompt.builder().promptId(45L).build();
        MockTestSection section = MockTestSection.builder()
                .sectionId(46L)
                .sectionType(SkillType.READING)
                .build();
        MockTest mockTest = MockTest.builder()
                .mockTestId(47L)
                .readingQuizzes(new ArrayList<>(List.of(quiz)))
                .listeningParts(new ArrayList<>(List.of(part)))
                .writingPrompts(new ArrayList<>(List.of(prompt)))
                .sections(new ArrayList<>(List.of(section)))
                .build();
        when(mockTestRepository.findById(47L)).thenReturn(Optional.of(mockTest));

        adminService.deleteMockTest(47L);

        assertNotNull(mockTest.getDeletedAt());
        assertSame(quiz, mockTest.getReadingQuizzes().get(0));
        assertSame(part, mockTest.getListeningParts().get(0));
        assertSame(prompt, mockTest.getWritingPrompts().get(0));
        assertSame(section, mockTest.getSections().get(0));
        verify(mockTestRepository).save(mockTest);
        verify(mockTestRepository, never()).deleteById(47L);
        verify(mockTestRepository, never()).delete(mockTest);
    }

    @Test
    void deleteListeningPartArchivesAndRetainsAudio() {
        ListeningQuestion question = ListeningQuestion.builder().questionId(48L).build();
        QuestionOption option = QuestionOption.builder().optionId(49L).build();
        question.setOptions(new ArrayList<>(List.of(option)));
        List<ListeningQuestion> questions = new ArrayList<>(List.of(question));
        ListeningPart part = ListeningPart.builder()
                .partId(50L)
                .audioUrl("/api/v1/listening/audio/dummy.mp3")
                .questions(questions)
                .build();
        when(listeningPartRepository.findById(50L)).thenReturn(Optional.of(part));

        adminListeningService.deletePart(50L);

        assertNotNull(part.getDeletedAt());
        assertEquals("/api/v1/listening/audio/dummy.mp3", part.getAudioUrl());
        assertSame(questions, part.getQuestions());
        assertSame(option, part.getQuestions().get(0).getOptions().get(0));
        verify(listeningPartRepository).save(part);
        verify(listeningPartRepository, never()).delete(part);
        verify(listeningPartRepository, never()).deleteById(50L);
    }

    @Test
    void listeningDatabaseFailureCannotDeleteOrChangeAudio() {
        ListeningPart part = ListeningPart.builder()
                .partId(51L)
                .audioUrl("/api/v1/listening/audio/still-present.mp3")
                .build();
        when(listeningPartRepository.findById(51L)).thenReturn(Optional.of(part));
        when(listeningPartRepository.save(part))
                .thenThrow(new DataIntegrityViolationException("simulated DB failure"));

        assertThrows(DataIntegrityViolationException.class,
                () -> adminListeningService.deletePart(51L));

        assertEquals("/api/v1/listening/audio/still-present.mp3", part.getAudioUrl());
        verify(listeningPartRepository, never()).delete(part);
    }

    @Test
    void restoreClearsSoftDeleteMarkerForEveryArchivableContentType() {
        java.time.LocalDateTime archivedAt = java.time.LocalDateTime.now();
        ReadingQuiz quiz = ReadingQuiz.builder().quizId(52L).deletedAt(archivedAt).build();
        WritingPrompt prompt = WritingPrompt.builder().promptId(53L).deletedAt(archivedAt).build();
        MockTest mockTest = MockTest.builder().mockTestId(54L).deletedAt(archivedAt).build();
        ListeningPart part = ListeningPart.builder().partId(55L).deletedAt(archivedAt).build();
        when(readingQuizRepository.findIncludingDeletedById(52L)).thenReturn(Optional.of(quiz));
        when(writingPromptRepository.findIncludingDeletedById(53L)).thenReturn(Optional.of(prompt));
        when(mockTestRepository.findIncludingDeletedById(54L)).thenReturn(Optional.of(mockTest));
        when(listeningPartRepository.findIncludingDeletedById(55L)).thenReturn(Optional.of(part));

        adminService.restoreReadingQuiz(52L);
        adminService.restoreWritingPrompt(53L);
        adminService.restoreMockTest(54L);
        adminListeningService.restorePart(55L);

        assertNull(quiz.getDeletedAt());
        assertNull(prompt.getDeletedAt());
        assertNull(mockTest.getDeletedAt());
        assertNull(part.getDeletedAt());
        verify(readingQuizRepository).save(quiz);
        verify(writingPromptRepository).save(prompt);
        verify(mockTestRepository).save(mockTest);
        verify(listeningPartRepository).save(part);
    }
}
