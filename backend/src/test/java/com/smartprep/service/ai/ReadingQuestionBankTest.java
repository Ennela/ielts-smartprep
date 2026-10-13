package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.AdminReadingImportRequest;
import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.response.AdminReadingQuizResponse;
import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.AdaptiveService;
import com.smartprep.service.AdminService;
import com.smartprep.service.ReadingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The Reading bank: stored templates served before Gemini, and passages imported from NotebookLM. */
@ExtendWith(MockitoExtension.class)
class ReadingQuestionBankTest {

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private ReadingPromptBuilder promptBuilder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AdaptiveService adaptiveService;
    @Mock private ReadingQueryService readingQueryService;
    @Mock private ReadingFallbackSource fallbackSource;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ReadingGenerationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).username("learner").build();
    }

    private static ReadingQuiz quizWithQuestions(Difficulty difficulty, int count) {
        ReadingQuiz quiz = ReadingQuiz.builder()
                .topic(Topic.HEALTH).difficulty(difficulty).passageText(difficulty + " passage").build();
        List<ReadingQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add(ReadingQuestion.builder().quiz(quiz).questionType(QuestionType.TFNG)
                    .questionText("Q" + i).correctAnswer("TRUE").orderIndex(i).build());
        }
        quiz.setQuestions(questions);
        return quiz;
    }

    private ReadingGenerateRequest request(String difficulty, Integer passageCount) {
        ReadingGenerateRequest request = new ReadingGenerateRequest();
        request.setTopic("HEALTH");
        request.setDifficulty(difficulty);
        request.setPassageCount(passageCount);
        return request;
    }

    @Test
    @DisplayName("a single passage comes from the bank, without calling Gemini, while the learner has unseen templates")
    void singlePassage_servedFromBankFirst() {
        ReadingQuiz banked = quizWithQuestions(Difficulty.PASSAGE_1, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.copyUnseenTemplate(user, Topic.HEALTH, Difficulty.PASSAGE_1, "ACADEMIC"))
                .thenReturn(Optional.of(banked));
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(inv -> inv.getArgument(0));
        when(readingQueryService.mapToQuizResponse(banked))
                .thenReturn(ReadingQuizResponse.builder().passageText(banked.getPassageText()).build());

        ReadingQuizResponse response = service.generateQuiz(1L, request("PASSAGE_1", 1));

        assertEquals("PASSAGE_1 passage", response.getPassageText());
        verify(quizRepository).save(banked);
        verifyNoInteractions(geminiClient);
    }

    @Test
    @DisplayName("a single passage goes to Gemini once the bank has nothing unseen left")
    void singlePassage_geminiWhenBankIsUsedUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.copyUnseenTemplate(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any())).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any(), any())).thenReturn("user");
        when(geminiClient.generate(anyString(), anyString())).thenReturn("""
                {"passage": "A. Text.", "questionGroups": [{"groupType": "TFNG",
                  "questions": [{"questionText": "S", "correctAnswer": "TRUE"}]}]}
                """);
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(inv -> inv.getArgument(0));
        when(readingQueryService.mapToQuizResponse(any())).thenReturn(ReadingQuizResponse.builder().build());

        service.generateQuiz(1L, request("PASSAGE_1", 1));

        verify(geminiClient).generate("system", "user");
    }

    @Test
    @DisplayName("a full test comes from the bank when each passage has an unseen template, numbered 1..n across passages")
    void fullTest_servedFromBankAndRenumbered() {
        ReadingQuiz p1 = quizWithQuestions(Difficulty.PASSAGE_1, 2);
        ReadingQuiz p2 = quizWithQuestions(Difficulty.PASSAGE_2, 2);
        ReadingQuiz p3 = quizWithQuestions(Difficulty.PASSAGE_3, 2);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.copyUnseenTemplate(eq(user), eq(Topic.HEALTH), any(), eq("ACADEMIC")))
                .thenAnswer(inv -> Optional.of(switch ((Difficulty) inv.getArgument(2)) {
                    case PASSAGE_1 -> p1;
                    case PASSAGE_2 -> p2;
                    case PASSAGE_3 -> p3;
                }));
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(inv -> inv.getArgument(0));
        when(readingQueryService.mapToQuizResponse(any())).thenReturn(ReadingQuizResponse.builder().build());

        service.generateQuiz(1L, request("PASSAGE_1", 3));

        assertEquals(List.of(1, 2), p1.getQuestions().stream().map(ReadingQuestion::getOrderIndex).toList());
        assertEquals(List.of(3, 4), p2.getQuestions().stream().map(ReadingQuestion::getOrderIndex).toList());
        assertEquals(List.of(5, 6), p3.getQuestions().stream().map(ReadingQuestion::getOrderIndex).toList());
        verifyNoInteractions(geminiClient);
    }

    @Test
    @DisplayName("an imported passage is parsed like a Gemini one, code fences dropped and evidence tags lifted out")
    void import_parsesFencedJsonWithEvidence() {
        String content = """
                ```json
                {
                  "passage": "A. Sleep matters. [ANS_1]Adults need seven hours.[/ANS_1]",
                  "questionGroups": [{
                    "groupLabel": "Questions 1: TRUE, FALSE or NOT GIVEN?",
                    "groupType": "TFNG",
                    "questions": [{"questionText": "Adults need seven hours of sleep.", "correctAnswer": "true",
                                   "explanation": "Paragraph A."}]
                  }]
                }
                ```
                """;

        ReadingQuiz quiz = service.parseImportedQuiz(content, Topic.HEALTH, Difficulty.PASSAGE_1);

        assertEquals("A. Sleep matters. Adults need seven hours.", quiz.getPassageText());
        assertNull(quiz.getUser());
        ReadingQuestion q = quiz.getQuestions().get(0);
        assertEquals("TRUE", q.getCorrectAnswer());
        assertEquals("Adults need seven hours.", q.getEvidenceText());
    }

    @Test
    @DisplayName("an imported passage is saved as a bank template awaiting review, so learners do not get it yet")
    void import_savedAsTemplateAwaitingReview() {
        AdminService adminService = new AdminService(null, null, null, quizRepository, null, null, service, promptBuilder);
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminReadingQuizResponse response = adminService.importReadingQuiz(AdminReadingImportRequest.builder()
                .topic("health").difficulty("passage_2")
                .content("""
                        {"passage": "A. Text.", "questionGroups": [{"groupType": "YNNG",
                          "questions": [{"questionText": "S", "correctAnswer": "no"}]}]}
                        """)
                .build());

        ArgumentCaptor<ReadingQuiz> saved = ArgumentCaptor.forClass(ReadingQuiz.class);
        verify(quizRepository).save(saved.capture());
        assertTrue(saved.getValue().getIsTemplate());
        assertEquals(ContentStatus.AI_IMPORTED, saved.getValue().getContentStatus());
        assertEquals("NOTEBOOKLM", saved.getValue().getSource());
        assertEquals("PASSAGE_2", response.getDifficulty());
        assertEquals("AI_IMPORTED", response.getContentStatus());
        assertEquals("NO", response.getQuestions().get(0).getCorrectAnswer());
    }

    @Test
    @DisplayName("the NotebookLM chat message is short and names the format source the instructions are filed under")
    void notebookLm_messageIsShortAndNamesTheSource() {
        ReadingPromptBuilder builder = new ReadingPromptBuilder();
        for (Difficulty difficulty : Difficulty.values()) {
            String title = builder.notebookLmSourceTitle(difficulty);
            String instructions = builder.buildNotebookLmInstructions(difficulty);
            String message = builder.buildNotebookLmMessage(Topic.HEALTH, difficulty);

            assertTrue(instructions.startsWith("# " + title));
            assertTrue(instructions.contains("questionGroups"));
            assertTrue(message.contains("\"" + title + "\""));
            assertTrue(message.length() < 500, "chat message is " + message.length() + " chars");
        }
    }

    @Test
    @DisplayName("an import that is not the expected JSON is a bad request, not an AI failure")
    void import_rejectsMalformedContent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseImportedQuiz("Here is your passage: ...", Topic.HEALTH, Difficulty.PASSAGE_1));
        assertTrue(ex.getMessage().startsWith("Cannot import this passage"));
    }

    @Test
    @DisplayName("the bank copies a random unseen template for the learner, linked to its template")
    void fallbackSource_copiesUnseenTemplate() {
        ReadingFallbackSource source = new ReadingFallbackSource(quizRepository);
        ReadingQuiz template = quizWithQuestions(Difficulty.PASSAGE_2, 1);
        template.setQuizId(42L);
        when(quizRepository.findUnseenPublishedTemplates(eq(Topic.HEALTH), eq(Difficulty.PASSAGE_2), eq(1L), any()))
                .thenReturn(List.of(template));

        ReadingQuiz copy = source.copyUnseenTemplate(user, Topic.HEALTH, Difficulty.PASSAGE_2, "ACADEMIC").orElseThrow();

        assertSame(user, copy.getUser());
        assertEquals(42L, copy.getParentTemplateId());
        assertFalse(copy.getIsTemplate());
        assertEquals(1, copy.getQuestions().size());

        when(quizRepository.findUnseenPublishedTemplates(any(), any(), any(), any())).thenReturn(List.of());
        assertTrue(source.copyUnseenTemplate(user, Topic.HEALTH, Difficulty.PASSAGE_2, "ACADEMIC").isEmpty());
    }
}
