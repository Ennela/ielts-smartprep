package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.repository.*;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.ListeningGenerationService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListeningServiceTest {

    // === ListeningGenerationService dependencies ===
    @Mock private ListeningPartRepository partRepository;
    @Mock private UserRepository userRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private ListeningPromptBuilder promptBuilder;
    @Mock private TtsService ttsService;
    @Mock private AudioGenerationService audioGenerationService;
    @Mock private org.springframework.cache.CacheManager cacheManager;
    @Mock private AdaptiveService adaptiveService;
    @Mock private ListeningQueryService listeningQueryService;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ListeningGenerationService listeningGenerationService;

    // === ListeningAudioService (separate tests) ===
    private ListeningAudioService listeningAudioService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(1L)
                .username("teststudent")
                .build();
        listeningAudioService = new ListeningAudioService(partRepository, ttsService, audioGenerationService);
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
        when(promptBuilder.buildGeneratePrompt(anyInt(), anyString(), any())).thenReturn("mock prompt");
        when(ttsService.isAvailable()).thenReturn(false);
        when(geminiClient.generate(anyString(), anyString())).thenReturn(mockAiResponse);
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

        // Mock the mapping method on ListeningQueryService
        when(listeningQueryService.toPartResponse(any(ListeningPart.class))).thenAnswer(invocation -> {
            ListeningPart part = invocation.getArgument(0);
            return ListeningPartResponse.builder()
                    .partId(part.getPartId())
                    .partNumber(part.getPartNumber())
                    .title(part.getTitle())
                    .topic(part.getTopic())
                    .audioUrl(part.getAudioUrl())
                    .audioStatus(AudioStatus.PENDING.name())
                    .durationSeconds(part.getDurationSeconds())
                    .questionCount(part.getQuestions().size())
                    .questions(part.getQuestions().stream().map(q ->
                            ListeningPartResponse.QuestionDto.builder()
                                    .questionId(q.getQuestionId())
                                    .questionType(q.getQuestionType().name())
                                    .questionText(q.getQuestionText())
                                    .orderIndex(q.getOrderIndex())
                                    .options(q.getOptions() != null ? q.getOptions().stream().map(o ->
                                            com.smartprep.dto.response.QuestionOptionResponse.builder()
                                                    .optionId(o.getOptionId())
                                                    .label(o.getLabel())
                                                    .content(o.getContent())
                                                    .build()
                                    ).toList() : null)
                                    .build()
                    ).toList())
                    .build();
        });

        ListeningPartResponse response = listeningGenerationService.generatePart(1L, 1, "gym");

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

    // ========== Audio Tests (using ListeningAudioService) ==========

    @Test
    @DisplayName("cleanScript: removes ANS markers but keeps text inside")
    void cleanScript_removesAnsMarkers() {
        String script = "The opening hours are from [ANS_1]nine in the morning[/ANS_1] until six.";
        String result = listeningAudioService.cleanScript(script);
        assertEquals("The opening hours are from nine in the morning until six.", result);
    }

    @Test
    @DisplayName("cleanScript: handles multiple ANS markers")
    void cleanScript_handlesMultipleMarkers() {
        String script = "Name: [ANS_1]Davies[/ANS_1], Phone: [ANS_2]07123456[/ANS_2]";
        String result = listeningAudioService.cleanScript(script);
        assertEquals("Name: Davies, Phone: 07123456", result);
    }

    @Test
    @DisplayName("cleanScript: returns empty string for null input")
    void cleanScript_nullInput_returnsEmpty() {
        assertEquals("", listeningAudioService.cleanScript(null));
    }

    @Test
    @DisplayName("generateAudio: throws when part not found")
    void generateAudio_partNotFound_throwsException() {
        when(partRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> listeningAudioService.generateAudio(999L));
    }

    @Test
    @DisplayName("generateAudio: skips if already READY")
    void generateAudio_alreadyReady_skips() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L)
                .audioStatus(AudioStatus.READY)
                .build();
        when(partRepository.findById(1L)).thenReturn(Optional.of(part));

        listeningAudioService.generateAudio(1L);

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

        assertThrows(IllegalStateException.class, () -> listeningAudioService.generateAudio(1L));
    }

    @Test
    @DisplayName("generateAudioAsync: delegates to AudioGenerationService")
    void generateAudioAsync_delegatesToAudioGenerationService() {
        doNothing().when(audioGenerationService).generateAudioAsync(1L);

        listeningAudioService.generateAudioAsync(1L);

        verify(audioGenerationService).generateAudioAsync(1L);
    }
}
