package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Guards the question-paper endpoints against ever serving the answer key.
 *
 * These endpoints are what a candidate can call while the exam is still open, so a
 * regression here is a silent cheat channel rather than a visible bug. Each test
 * asserts on the serialized JSON as well as the DTO, because the leak the users
 * would actually see is the JSON.
 */
@ExtendWith(MockitoExtension.class)
class AnswerLeakRegressionTest {

    @Mock
    private ReadingQuizRepository quizRepository;
    @Mock
    private ScoreHistoryRepository scoreHistoryRepository;
    @InjectMocks
    private ReadingQueryService readingQueryService;

    @Mock
    private ListeningPartRepository partRepository;
    @Mock
    private ListeningTestRepository testRepository;
    @Mock
    private ObjectMapper listeningObjectMapper;

    private static final String LEAKY_EXPLANATION = "leaky-explanation";
    private static final String LEAKY_EVIDENCE = "leaky-evidence";
    private static final String LEAKY_TRANSCRIPT = "leaky-transcript";

    private static ObjectMapper json() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static List<QuestionOption> options() {
        return List.of(
                QuestionOption.builder().optionId(1L).label("A").content("alpha").isCorrect(false).orderIndex(1).build(),
                QuestionOption.builder().optionId(2L).label("B").content("beta").isCorrect(true).orderIndex(2).build());
    }

    private static ReadingQuiz quizWithAnswers() {
        ReadingQuestion question = ReadingQuestion.builder()
                .questionId(11L)
                .questionType(QuestionType.MCQ)
                .questionText("Which one?")
                .options(options())
                .correctAnswer("B")
                .explanation(LEAKY_EXPLANATION)
                .evidenceText(LEAKY_EVIDENCE)
                .orderIndex(1)
                .build();
        return ReadingQuiz.builder()
                .quizId(1L)
                .topic(Topic.TECHNOLOGY)
                .difficulty(Difficulty.PASSAGE_1)
                .passageText("passage body")
                .questions(List.of(question))
                .build();
    }

    private static ListeningPart partWithAnswers() {
        ListeningQuestion question = ListeningQuestion.builder()
                .questionId(21L)
                .questionType(QuestionType.MCQ)
                .questionText("Which one?")
                .options(options())
                .correctAnswer("A")
                .orderIndex(1)
                .build();
        return ListeningPart.builder()
                .partId(5L)
                .partNumber(1)
                .title("Sports centre enrolment")
                .topic("daily life")
                .audioUrl("part1.mp3")
                .transcriptText(LEAKY_TRANSCRIPT)
                .questions(List.of(question))
                .build();
    }

    private ListeningQueryService listeningQueryService() {
        return new ListeningQueryService(partRepository, testRepository, scoreHistoryRepository, listeningObjectMapper);
    }

    @Test
    @DisplayName("GET /reading/{quizId} never carries correctAnswer, explanation, evidence or isCorrect")
    void readingGetQuiz_neverExposesAnswers() throws Exception {
        when(quizRepository.findByQuizIdAndUserUserId(1L, 7L)).thenReturn(Optional.of(quizWithAnswers()));

        ReadingQuizResponse res = readingQueryService.getQuiz(1L, 7L);

        assertThat(res.getQuestions().get(0).getOptions())
                .allSatisfy(o -> assertThat(o.getIsCorrect()).isNull());

        String body = json().writeValueAsString(res);
        assertThat(body)
                .doesNotContain("correctAnswer")
                .doesNotContain(LEAKY_EXPLANATION)
                .doesNotContain(LEAKY_EVIDENCE)
                .doesNotContain("\"isCorrect\":true")
                .doesNotContain("\"isCorrect\":false");
    }

    @Test
    @DisplayName("GET /listening/parts/{partId} never carries correctAnswer or transcript")
    void listeningGetPartById_neverExposesAnswersOrTranscript() throws Exception {
        when(partRepository.findById(5L)).thenReturn(Optional.of(partWithAnswers()));

        ListeningPartResponse res = listeningQueryService().getPartById(5L);

        assertThat(res.getQuestions().get(0).getOptions())
                .allSatisfy(o -> assertThat(o.getIsCorrect()).isNull());

        String body = json().writeValueAsString(res);
        assertThat(body)
                .doesNotContain("correctAnswer")
                .doesNotContain(LEAKY_TRANSCRIPT)
                .doesNotContain("\"isCorrect\":true")
                .doesNotContain("\"isCorrect\":false");
    }

    @Test
    @DisplayName("GET /listening/parts never carries correctAnswer or transcript")
    void listeningGetAllParts_neverExposesAnswers() throws Exception {
        when(partRepository.findAllByOrderByPartNumberAscPartIdAsc()).thenReturn(List.of(partWithAnswers()));

        List<ListeningPartResponse> res = listeningQueryService().getAllParts();

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getQuestions().get(0).getOptions())
                .allSatisfy(o -> assertThat(o.getIsCorrect()).isNull());

        String body = json().writeValueAsString(res);
        assertThat(body)
                .doesNotContain("correctAnswer")
                .doesNotContain(LEAKY_TRANSCRIPT)
                .doesNotContain("\"isCorrect\":true");
    }

    @Test
    @DisplayName("an option mapped for the exam view serializes isCorrect as an explicit null")
    void examOption_serializesIsCorrectAsNull() throws Exception {
        when(quizRepository.findByQuizIdAndUserUserId(1L, 7L)).thenReturn(Optional.of(quizWithAnswers()));

        String body = json().writeValueAsString(readingQueryService.getQuiz(1L, 7L));

        assertThat(body).contains("\"isCorrect\":null");
    }
}
