package com.smartprep.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TtsService ttsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ttsService, "ttsEnabled", true);
        ReflectionTestUtils.setField(ttsService, "edgeTtsUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(ttsService, "speakingRate", "-8%");
        ttsService.init();
        // Inject mock restTemplate
        ReflectionTestUtils.setField(ttsService, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("parseSpeakers: dialogue with two speakers correctly splits into lines")
    void parseSpeakers_dialogue_correctlySplits() {
        String script = """
                Sarah: Hello, welcome to the gym. How can I help you?
                Mr. Davies: I'd like to enquire about membership options.
                Sarah: Of course! We have three types of membership.
                Mr. Davies: Could you tell me about the prices?
                """;

        List<TtsService.SpeakerLine> lines = ttsService.parseSpeakers(script);

        assertEquals(4, lines.size());
        assertEquals("Sarah", lines.get(0).speakerName());
        assertTrue(lines.get(0).text().contains("welcome to the gym"));
        assertEquals("Mr. Davies", lines.get(1).speakerName());
        assertTrue(lines.get(1).text().contains("membership options"));
        assertEquals("Sarah", lines.get(2).speakerName());
        assertEquals("Mr. Davies", lines.get(3).speakerName());
    }

    @Test
    @DisplayName("parseSpeakers: monologue produces single narrator entry")
    void parseSpeakers_monologue_singleSpeaker() {
        String script = "Today we will discuss the impact of climate change on coastal communities. " +
                "Research has shown that rising sea levels affect millions of people worldwide.";

        List<TtsService.SpeakerLine> lines = ttsService.parseSpeakers(script);

        assertEquals(1, lines.size());
        assertEquals("_NARRATOR_", lines.get(0).speakerName());
        assertTrue(lines.get(0).text().contains("climate change"));
    }

    @Test
    @DisplayName("parseSpeakers: consecutive lines from same speaker are merged")
    void parseSpeakers_sameSpeakerMerged() {
        String script = """
                John: First sentence.
                John: Second sentence.
                Mary: Hello there.
                """;

        List<TtsService.SpeakerLine> lines = ttsService.parseSpeakers(script);

        assertEquals(2, lines.size());
        assertEquals("John", lines.get(0).speakerName());
        assertTrue(lines.get(0).text().contains("First sentence"));
        assertTrue(lines.get(0).text().contains("Second sentence"));
        assertEquals("Mary", lines.get(1).speakerName());
    }

    @Test
    @DisplayName("concatenateMp3Segments: concatenates multiple byte arrays")
    void concatenateMp3Segments_combinesCorrectly() {
        byte[] seg1 = {1, 2, 3};
        byte[] seg2 = {4, 5};
        byte[] seg3 = {6, 7, 8, 9};

        byte[] result = ttsService.concatenateMp3Segments(List.of(seg1, seg2, seg3));

        assertEquals(9, result.length);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, result);
    }

    @Test
    @DisplayName("synthesizeSingle: calls restTemplate and returns response bytes")
    void synthesizeSingle_success_returnsBytes() {
        byte[] expectedBytes = {1, 2, 3, 4};
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(expectedBytes);

        byte[] result = ttsService.synthesizeSingle("Hello world", "en-US-GuyNeural");

        assertArrayEquals(expectedBytes, result);
        verify(restTemplate).getForObject(contains("text=Hello%20world"), eq(byte[].class));
    }

    @Test
    @DisplayName("synthesizeMultiVoice: dialogue synthesizes each line and combines")
    void synthesizeMultiVoice_dialogue_synthesizesAndCombines() {
        String script = """
                Sarah: Hello!
                John: Hi!
                """;
        byte[] sarahVoice = {1, 1};
        byte[] johnVoice = {2, 2};

        when(restTemplate.getForObject(contains("text=Hello!"), eq(byte[].class))).thenReturn(sarahVoice);
        when(restTemplate.getForObject(contains("text=Hi!"), eq(byte[].class))).thenReturn(johnVoice);

        byte[] result = ttsService.synthesizeMultiVoice(script);

        assertArrayEquals(new byte[]{1, 1, 2, 2}, result);
        verify(restTemplate, times(2)).getForObject(anyString(), eq(byte[].class));
    }

    @Test
    @DisplayName("synthesizeSingle: throws when HTTP call fails")
    void synthesizeSingle_failure_throwsException() {
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenThrow(new RuntimeException("Connection error"));

        assertThrows(RuntimeException.class, () -> ttsService.synthesizeSingle("Hello", "en-US-GuyNeural"));
    }
}
