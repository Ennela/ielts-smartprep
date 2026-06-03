package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.*;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.ListeningPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListeningServiceTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private ListeningTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private ListeningPromptBuilder promptBuilder;
    @Mock private TtsService ttsService;
    @Mock private StorageService storageService;
    @Mock private AudioGenerationService audioGenerationService;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ListeningService listeningService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(1L)
                .username("teststudent")
                .build();
    }

    @Test
    @DisplayName("should parse structured options correctly during generation")
    void generatePart_success() {
        String mockAiResponse = """
                {
                  "title": "Part 1 practice",
                  "topic": "gym membership",
                  "script": "Dialogue script here",
                  "questions": [
                    {
                      "questionNumber": 1,
                      "questionType": "MULTIPLE_CHOICE",
                      "questionText": "What Gym membership is chosen?",
                      "options": [
                        { "label": "A", "content": "Gold membership" },
                        { "label": "B", "content": "Silver membership" },
                        { "label": "C", "content": "Bronze membership" }
                      ],
                      "correctAnswer": "A"
                    }
                  ]
                }
                """;

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptBuilder.buildGeneratePrompt(anyInt(), anyString())).thenReturn("mock prompt");
        when(ttsService.isAvailable()).thenReturn(false); // TTS disabled for this test
        when(geminiClient.generateAndParse(anyString(), anyString(), any(GeminiClient.CheckedFunction.class)))
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, ListeningPart> parser = invocation.getArgument(2);
                    return parser.apply(mockAiResponse);
                });
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(invocation -> {
            ListeningPart part = invocation.getArgument(0);
            part.setPartId(10L);
            if (part.getQuestions() != null) {
                long id = 100L;
                for (ListeningQuestion q : part.getQuestions()) {
                    q.setQuestionId(id++);
                    if (q.getOptions() != null) {
                        long optId = 1000L;
                        for (QuestionOption opt : q.getOptions()) {
                            opt.setOptionId(optId++);
                        }
                    }
                }
            }
            return part;
        });

        ListeningPartResponse response = listeningService.generatePart(1L, 1, "gym");

        assertNotNull(response);
        assertEquals("Part 1 practice", response.getTitle());
        assertEquals(1, response.getQuestions().size());

        var qDto = response.getQuestions().get(0);
        assertEquals("What Gym membership is chosen?", qDto.getQuestionText());
        assertEquals("MCQ", qDto.getQuestionType());
        assertNotNull(qDto.getOptions());
        assertEquals(3, qDto.getOptions().size());
        assertEquals("A", qDto.getOptions().get(0).getLabel());
        assertEquals("Gold membership", qDto.getOptions().get(0).getContent());
    }

    // ========== Audio Generation Tests ==========

    @Test
    @DisplayName("cleanScript: removes ANS markers but keeps text inside")
    void cleanScript_removesAnsMarkers() {
        String script = "The opening hours are from [ANS_1]nine in the morning[/ANS_1] until six.";

        String result = listeningService.cleanScript(script);

        assertEquals("The opening hours are from nine in the morning until six.", result);
    }

    @Test
    @DisplayName("cleanScript: handles multiple ANS markers")
    void cleanScript_handlesMultipleMarkers() {
        String script = "Name: [ANS_1]Davies[/ANS_1], Phone: [ANS_2]07123456[/ANS_2]";

        String result = listeningService.cleanScript(script);

        assertEquals("Name: Davies, Phone: 07123456", result);
    }

    @Test
    @DisplayName("cleanScript: returns empty string for null input")
    void cleanScript_nullInput_returnsEmpty() {
        assertEquals("", listeningService.cleanScript(null));
    }

    @Test
    @DisplayName("generateAudio: throws when part not found")
    void generateAudio_partNotFound_throwsException() {
        when(partRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> listeningService.generateAudio(999L));
    }

    @Test
    @DisplayName("generateAudio: skips if already READY")
    void generateAudio_alreadyReady_skips() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L)
                .audioStatus(AudioStatus.READY)
                .build();
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));

        listeningService.generateAudio(1L);

        // Should not call TTS or save again
        verify(ttsService, never()).synthesizeMultiVoice(anyString());
    }

    @Test
    @DisplayName("generateAudio: throws when TTS unavailable")
    void generateAudio_ttsUnavailable_throws() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L)
                .audioStatus(AudioStatus.PENDING)
                .build();
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(ttsService.isAvailable()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> listeningService.generateAudio(1L));
    }

    @Test
    @DisplayName("generateAudioAsync: delegates to AudioGenerationService")
    void generateAudioAsync_delegatesToAudioGenerationService() {
        doNothing().when(audioGenerationService).generateAudioAsync(1L);

        listeningService.generateAudioAsync(1L);

        verify(audioGenerationService).generateAudioAsync(1L);
    }
}
