package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ListeningSubmitRequest;
import com.smartprep.dto.response.ListeningTestResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The result page draws the submit response itself, straight after the submit. Found on the
 * running app: a grouped part came back with no instructions, no map and no select count,
 * because only GET /{testId}/result carried the V54-V56 fields.
 */
@ExtendWith(MockitoExtension.class)
class ListeningSubmitResponseGroupsTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamAttemptService examAttemptService;

    @InjectMocks private ListeningGradingService listeningGradingService;

    @Test
    @DisplayName("the submit response carries each question's group, picture and select count")
    void submitResponseCarriesGroupFields() throws Exception {
        ListeningPart part = ListeningPart.builder().partId(1L).partNumber(2).questions(new ArrayList<>()).build();
        part.getQuestions().add(ListeningQuestion.builder()
                .questionId(11L).part(part).questionType(QuestionType.MCQ).questionText("Which TWO?")
                .correctAnswer("B").orderIndex(5).groupId(3).groupLabel("Questions 5-6: Choose TWO letters.")
                .selectCount(2).imageUrl("/api/v1/images/img_x.png").explanation("Said at 1:20").build());

        ListeningSubmitRequest request = new ListeningSubmitRequest();
        request.setTestMode("PRACTICE");
        request.setPartIds(List.of(1L));
        request.setAnswers(Map.of(11L, "B,D"));
        request.setAttemptId(9L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().userId(7L).build()));
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(testRepository.save(any(ListeningTest.class))).thenAnswer(i -> i.getArgument(0));

        ListeningTestResponse.QuestionResult q =
                listeningGradingService.submitTest(7L, request).getParts().get(0).getQuestions().get(0);

        assertThat(q.getIsCorrect()).isTrue();
        assertThat(q.getGroupId()).isEqualTo(3);
        assertThat(q.getGroupLabel()).isEqualTo("Questions 5-6: Choose TWO letters.");
        assertThat(q.getSelectCount()).isEqualTo(2);
        assertThat(q.getImageUrl()).isEqualTo("/api/v1/images/img_x.png");
        assertThat(q.getExplanation()).isEqualTo("Said at 1:20");
    }
}
