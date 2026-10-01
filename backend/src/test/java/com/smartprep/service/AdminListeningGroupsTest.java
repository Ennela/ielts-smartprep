package com.smartprep.service;

import com.smartprep.dto.request.AdminListeningPartRequest;
import com.smartprep.dto.request.AdminListeningPartRequest.QuestionRequest;
import com.smartprep.dto.response.AdminListeningPartResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.repository.ListeningPartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An admin can save a Listening part whose questions are grouped (V54): a box of options
 * that several questions choose from, or notes with numbered gaps.
 */
@ExtendWith(MockitoExtension.class)
class AdminListeningGroupsTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private AudioGenerationService audioGenerationService;

    @InjectMocks private AdminListeningService adminListeningService;

    private static AdminListeningPartRequest partWith(QuestionRequest... questions) {
        return AdminListeningPartRequest.builder()
                .partNumber(2).title("Park tour").topic("Leisure")
                .transcriptText("Guide: Welcome to the park.")
                .questions(List.of(questions))
                .build();
    }

    private static QuestionRequest matching(int order, String answer, String optionsJson) {
        return QuestionRequest.builder()
                .questionType("MATCHING_FEATURES").questionText("Café").correctAnswer(answer)
                .orderIndex(order).groupId(2)
                .groupLabel("Questions 16-20: Which location on the map?")
                .optionsJson(optionsJson)
                .build();
    }

    @Test
    @DisplayName("saves the group fields and returns them to the editor")
    void savesGroupFields() {
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> inv.getArgument(0));
        String options = "[\"A. car park\", \"B. cafe\", \"C. lake\"]";

        AdminListeningPartResponse response = adminListeningService.createPart(partWith(
                matching(1, "B", options),
                QuestionRequest.builder()
                        .questionType("FILL_BLANK").questionText("Blank 21").correctAnswer("bridge")
                        .orderIndex(2).groupId(3).groupLabel("Questions 21-25: Complete the notes.")
                        .groupContext("The walk starts at the ___21___.").wordLimit(2)
                        .explanation("  ")
                        .build()), "admin");

        ArgumentCaptor<ListeningPart> saved = ArgumentCaptor.forClass(ListeningPart.class);
        verify(partRepository).save(saved.capture());
        List<ListeningQuestion> questions = saved.getValue().getQuestions();
        assertThat(questions.get(0).getGroupId()).isEqualTo(2);
        assertThat(questions.get(0).getOptionsJson()).isEqualTo(options);
        assertThat(questions.get(1).getGroupContext()).isEqualTo("The walk starts at the ___21___.");
        assertThat(questions.get(1).getWordLimit()).isEqualTo(2);
        assertThat(questions.get(1).getExplanation()).as("blank becomes null").isNull();

        assertThat(response.getQuestions().get(0).getGroupLabel())
                .isEqualTo("Questions 16-20: Which location on the map?");
    }

    @Test
    @DisplayName("refuses group options that the exam page could not show")
    void refusesUnusableOptions() {
        // Saved as is, a group like this renders with nothing to choose from.
        for (String bad : List.of("A. car park, B. cafe", "[]", "{\"A\": \"car park\"}", "[1, 2]")) {
            assertThatThrownBy(() -> adminListeningService.createPart(partWith(matching(1, "B", bad)), "admin"))
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("JSON array of strings");
        }
        verify(partRepository, never()).save(any());
    }
}
