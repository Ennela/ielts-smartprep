package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.AdminListeningImportRequest;
import com.smartprep.dto.request.ListeningGenerateRequest;
import com.smartprep.dto.response.AdminListeningPartResponse;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningQuestionRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.AdaptiveService;
import com.smartprep.service.AdminListeningService;
import com.smartprep.service.AudioGenerationService;
import com.smartprep.service.ListeningQueryService;
import com.smartprep.service.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The Listening bank: published parts served before Gemini, and parts imported from NotebookLM. */
@ExtendWith(MockitoExtension.class)
class ListeningQuestionBankTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningQuestionRepository questionRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private ListeningPromptBuilder promptBuilder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AdaptiveService adaptiveService;
    @Mock private TtsService ttsService;
    @Mock private AudioGenerationService audioGenerationService;
    @Mock private ListeningQueryService listeningQueryService;
    @Mock private Executor ttsExecutor;
    @Mock private ListeningFallbackSource fallbackSource;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ListeningGenerationService service;

    private User user;

    /** A Part 1 as the Gemini prompt asks for it: one completion and one multiple-choice question. */
    private static final String PART_JSON = """
            {
              "partNumber": 1, "topic": "Hotel booking", "title": "Seaview Hotel Booking",
              "script": "Anna: I'd like a room for [ANS_1]three nights[/ANS_1]. The [ANS_2]sea view[/ANS_2] one, please.",
              "questions": [
                {"questionNumber": 1, "questionType": "FORM_COMPLETION",
                 "questionText": "Length of stay: _____", "correctAnswer": "three nights"},
                {"questionNumber": 2, "questionType": "MULTIPLE_CHOICE",
                 "questionText": "Which room does Anna choose?",
                 "options": [{"label": "A", "content": "garden view"}, {"label": "B", "content": "sea view"},
                             {"label": "C", "content": "city view"}],
                 "correctAnswer": "B"}
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).username("learner").build();
    }

    private static ListeningPartResponse banked(long partId, int partNumber) {
        return ListeningPartResponse.builder().partId(partId).partNumber(partNumber).title("Bank part " + partNumber).build();
    }

    private void geminiReturnsPart() {
        when(promptBuilder.buildGeneratePrompt(anyInt(), any(), any())).thenReturn("prompt");
        when(geminiClient.generate(anyString(), anyString())).thenReturn(PART_JSON);
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(listeningQueryService.toPartResponse(any(ListeningPart.class)))
                .thenAnswer(inv -> ListeningPartResponse.builder().title(((ListeningPart) inv.getArgument(0)).getTitle()).build());
    }

    @Test
    @DisplayName("a single part comes from the bank, without calling Gemini or saving a part, while the learner has unseen ones")
    void singlePart_servedFromBankFirst() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.unseenPublishedPart(1L, 2, "EDUCATION")).thenReturn(Optional.of(banked(7L, 2)));

        ListeningPartResponse response = service.generatePart(1L, 2, "EDUCATION");

        assertEquals(7L, response.getPartId());
        verifyNoInteractions(geminiClient);
        verify(partRepository, never()).save(any());
        verifyNoInteractions(audioGenerationService);
    }

    @Test
    @DisplayName("a single part goes to Gemini once the bank has nothing unseen left")
    void singlePart_geminiWhenBankIsUsedUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.unseenPublishedPart(1L, 1, "ACCOMMODATION")).thenReturn(Optional.empty());
        geminiReturnsPart();

        ListeningPartResponse response = service.generatePart(1L, 1, "ACCOMMODATION");

        assertEquals("Seaview Hotel Booking", response.getTitle());
        verify(geminiClient).generate(anyString(), eq("prompt"));
    }

    @Test
    @DisplayName("an adaptive request that targets a question type skips the bank")
    void adaptive_skipsBank() {
        AdaptiveService.AdaptiveConfig config = new AdaptiveService.AdaptiveConfig();
        config.nextDifficulty = "1";
        config.focusQuestionType = "MCQ";
        when(adaptiveService.suggestNextConfig(1L, SkillType.LISTENING)).thenReturn(config);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        geminiReturnsPart();

        service.generatePart(1L, new ListeningGenerateRequest(null, "SCIENCE", true));

        verify(fallbackSource, never()).unseenPublishedPart(any(), anyInt(), any());
        verify(promptBuilder).buildGeneratePrompt(1, "SCIENCE", "MCQ");
    }

    @Test
    @DisplayName("a full test comes from the bank when each of the four parts has an unseen one")
    void fullTest_servedFromBank() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.unseenPublishedPart(eq(1L), anyInt(), eq("CULTURE")))
                .thenAnswer(inv -> Optional.of(banked(10L + (int) inv.getArgument(1), inv.getArgument(1))));

        List<ListeningPartResponse> parts = service.generateFullTest(1L, "CULTURE");

        assertEquals(List.of(1, 2, 3, 4), parts.stream().map(ListeningPartResponse::getPartNumber).toList());
        assertEquals(List.of(11L, 12L, 13L, 14L), parts.stream().map(ListeningPartResponse::getPartId).toList());
        verifyNoInteractions(geminiClient);
        verify(partRepository, never()).save(any());
    }

    @Test
    @DisplayName("a full test is generated by Gemini when the bank lacks any one part")
    void fullTest_geminiWhenAPartIsMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fallbackSource.unseenPublishedPart(eq(1L), anyInt(), eq("CULTURE")))
                .thenAnswer(inv -> (int) inv.getArgument(1) == 3 ? Optional.empty() : Optional.of(banked(1L, inv.getArgument(1))));
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(ttsExecutor).execute(any());
        geminiReturnsPart();

        List<ListeningPartResponse> parts = service.generateFullTest(1L, "CULTURE");

        assertEquals(4, parts.size());
        verify(geminiClient, times(4)).generate(anyString(), anyString());
    }

    @Test
    @DisplayName("an imported part is parsed like a Gemini one, code fences dropped")
    void import_parsesFencedJson() {
        ListeningPart part = service.parseImportedPart("```json\n" + PART_JSON + "\n```", 1, "Accommodation");

        assertEquals(1, part.getPartNumber());
        assertEquals("Seaview Hotel Booking", part.getTitle());
        assertEquals(2, part.getQuestions().size());
        assertEquals(QuestionType.FILL_BLANK, part.getQuestions().get(0).getQuestionType());
        assertEquals(QuestionType.MCQ, part.getQuestions().get(1).getQuestionType());
        assertEquals(3, part.getQuestions().get(1).getOptions().size());
        assertTrue(part.getQuestions().get(1).getOptions().get(1).getIsCorrect());
    }

    @Test
    @DisplayName("an import that is not the expected JSON is a bad request, not an AI failure")
    void import_rejectsMalformedContent() {
        IllegalArgumentException notJson = assertThrows(IllegalArgumentException.class,
                () -> service.parseImportedPart("Here is your part: ...", 1, "Accommodation"));
        assertTrue(notJson.getMessage().startsWith("Cannot import this part"));

        IllegalArgumentException noQuestions = assertThrows(IllegalArgumentException.class,
                () -> service.parseImportedPart("{\"script\": \"Hello.\", \"questions\": []}", 1, "Accommodation"));
        assertTrue(noQuestions.getMessage().contains("must contain questions"));
    }

    @Test
    @DisplayName("an imported part is saved awaiting review, under the learner topic, and its audio is queued after the save")
    void import_savedAwaitingReviewThenAudioQueued() {
        AdminListeningService admin = new AdminListeningService(partRepository, audioGenerationService, service, new ListeningPromptBuilder());
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> {
            ListeningPart p = inv.getArgument(0);
            p.setPartId(99L);
            return p;
        });

        AdminListeningPartResponse response = admin.importPart(AdminListeningImportRequest.builder()
                .partNumber(1).topic("ACCOMMODATION").content(PART_JSON).build(), "admin2026");

        ArgumentCaptor<ListeningPart> saved = ArgumentCaptor.forClass(ListeningPart.class);
        InOrder order = inOrder(partRepository, audioGenerationService);
        order.verify(partRepository).save(saved.capture());
        order.verify(audioGenerationService).generateAudioAsync(99L);
        assertEquals(ContentStatus.AI_IMPORTED, saved.getValue().getContentStatus());
        assertEquals(AudioStatus.PENDING, saved.getValue().getAudioStatus());
        assertEquals("NOTEBOOKLM", saved.getValue().getSource());
        assertNotNull(saved.getValue().getImportedAt());
        assertEquals("Accommodation", saved.getValue().getTopic());
        assertEquals("AI_IMPORTED", response.getContentStatus());
        assertEquals(2, response.getQuestionCount());
    }

    @Test
    @DisplayName("the NotebookLM chat message is short and names the format source, which leaves the topic to it")
    void notebookLm_messageIsShortAndNamesTheSource() {
        ListeningPromptBuilder builder = new ListeningPromptBuilder();
        for (int partNumber = 1; partNumber <= 4; partNumber++) {
            String title = builder.notebookLmSourceTitle(partNumber);
            String instructions = builder.buildNotebookLmInstructions(partNumber);
            String message = builder.buildNotebookLmMessage(partNumber, "Accommodation");

            assertEquals("IELTS Listening format - Part " + partNumber, title);
            assertTrue(instructions.startsWith("# " + title));
            assertTrue(instructions.contains("Topic (if specified): the topic given in the chat message"));
            assertTrue(instructions.contains("SEQUENTIAL ORDER"));
            assertTrue(message.contains("\"" + title + "\""));
            assertTrue(message.contains("Accommodation"));
            assertTrue(message.length() < 500, "chat message is " + message.length() + " chars");
        }
    }

    @Test
    @DisplayName("the bank serves a random unseen published part as it is, or nothing once they are used up")
    void fallbackSource_servesUnseenPart() {
        ListeningFallbackSource source = new ListeningFallbackSource(partRepository, listeningQueryService);
        ListeningPart part = ListeningPart.builder().partId(5L).partNumber(3).title("Seminar").build();
        when(partRepository.findUnseenPublishedParts(eq(3), eq("EDUCATION"), eq(1L), any())).thenReturn(List.of(part));
        when(listeningQueryService.toPartResponse(part)).thenReturn(banked(5L, 3));

        assertEquals(5L, source.unseenPublishedPart(1L, 3, "EDUCATION").orElseThrow().getPartId());

        when(partRepository.findUnseenPublishedParts(eq(3), isNull(), eq(1L), any())).thenReturn(List.of());
        assertTrue(source.unseenPublishedPart(1L, 3, " ").isEmpty());
    }
}
