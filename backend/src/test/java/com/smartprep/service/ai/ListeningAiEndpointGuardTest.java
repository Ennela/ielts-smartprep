package com.smartprep.service.ai;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningQuestionRepository;
import com.smartprep.repository.ListeningTestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Both AI post-analysis endpoints quote the transcript back to the caller:
 * analyzeQuestion returns the sentence holding the answer, extractVocabulary
 * returns whole sentences as usage examples. A candidate mid-exam must not be
 * able to reach either one, so each must refuse before Gemini is ever called.
 */
@ExtendWith(MockitoExtension.class)
class ListeningAiEndpointGuardTest {

    private static final Long USER_ID = 7L;
    private static final Long PART_ID = 5L;
    private static final Long QUESTION_ID = 21L;

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningQuestionRepository questionRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private GeminiClient geminiClient;
    @InjectMocks private ListeningGenerationService service;

    private static ListeningQuestion questionOnPart() {
        ListeningPart part = ListeningPart.builder()
                .partId(PART_ID)
                .partNumber(1)
                .title("Sports centre enrolment")
                .audioUrl("part1.mp3")
                .transcriptText("the deposit is ninety pounds")
                .build();
        return ListeningQuestion.builder()
                .questionId(QUESTION_ID)
                .questionType(QuestionType.FILL_BLANK)
                .questionText("The deposit is ___ pounds.")
                .correctAnswer("ninety")
                .orderIndex(1)
                .part(part)
                .build();
    }

    @Test
    @DisplayName("analyzeQuestion refuses when the user has not submitted a test with that part")
    void analyzeQuestion_notSubmitted_refusedBeforeCallingAi() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(questionOnPart()));
        when(testRepository.existsSubmittedPart(USER_ID, PART_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.analyzeQuestion(USER_ID, QUESTION_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(geminiClient, never()).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("extractVocabulary refuses before even loading the part when not submitted")
    void extractVocabulary_notSubmitted_refusedBeforeLoadingTranscript() {
        when(testRepository.existsSubmittedPart(USER_ID, PART_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.extractVocabulary(USER_ID, PART_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(partRepository, never()).findById(any());
        verify(geminiClient, never()).generate(anyString(), anyString());
    }
}
