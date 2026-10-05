package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.model.entity.SpeakingPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Parsing and banding of Gemini's Speaking grade; no network. */
class SpeakingGradingServiceTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final SpeakingGradingService service = new SpeakingGradingService(gemini, new ObjectMapper());

    private static final String GOOD = """
            {"transcript": "I come from Hue.", "fluencyCoherence": 6.5, "lexicalResource": "6",
             "grammaticalRange": 5.5, "pronunciation": 7.2,
             "summary": "A clear answer.", "strengths": ["Good range"], "improvements": ["Extend answers"],
             "criteria": {"fluencyCoherence": "Few pauses.", "pronunciation": "Clear."}}
            """;

    @Test
    @DisplayName("rounds each band to a half and the overall with IELTS rounding")
    void bands() throws Exception {
        SpeakingGradingService.Result r = service.toResult(service.parseAndValidate(GOOD, 0), 0);

        assertEquals(new BigDecimal("6.5"), r.getFluencyBand());
        assertEquals(new BigDecimal("6.0"), r.getLexicalBand());
        assertEquals(new BigDecimal("5.5"), r.getGrammarBand());
        assertEquals(new BigDecimal("7.0"), r.getPronunciationBand());
        // (6.5 + 6.0 + 5.5 + 7.0) / 4 = 6.25 -> 6.5
        assertEquals(new BigDecimal("6.5"), r.getOverallBand());
        assertEquals("I come from Hue.", r.getTranscript());
        assertEquals(2, r.getCriteriaComments().size());
        assertTrue(r.getAnswers().isEmpty());
    }

    @Test
    @DisplayName("Part 1/3: needs one answer per recording, and keeps each transcript and comment")
    void perAnswer() throws Exception {
        String two = GOOD.replace("\"criteria\":", "\"answers\": [{\"transcript\": \"Hue.\", \"comment\": \"Say more.\"},"
                + " {\"transcript\": \"Yes.\"}], \"criteria\":");

        SpeakingGradingService.Result r = service.toResult(service.parseAndValidate(two, 2), 2);

        assertEquals("Hue.", r.getAnswers().get(0).transcript());
        assertEquals("Say more.", r.getAnswers().get(0).comment());
        assertEquals("", r.getAnswers().get(1).comment());
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(two, 3));
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(GOOD, 2));
    }

    @Test
    @DisplayName("sends each Part 1/3 answer after the question it answers, in one call")
    @SuppressWarnings("unchecked")
    void labelsEachClip() throws Exception {
        when(gemini.gradeAudioAndParse(anyString(), anyString(), anyList(), any())).thenReturn(new ObjectMapper().readTree(
                GOOD.replace("\"criteria\":", "\"answers\": [{}, {}], \"criteria\":")));
        SpeakingPrompt hometown = SpeakingPrompt.builder().part(1).topic("Hometown").questionText("Q1?\nQ2?").build();
        byte[] a = {1};
        byte[] b = {2};

        service.grade(hometown, List.of("Q1?", "Q2?"), List.of(
                new SpeakingGradingService.Recording(a, "audio/webm", 20),
                new SpeakingGradingService.Recording(b, "audio/webm", 15)));

        ArgumentCaptor<List<GeminiClient.AudioClip>> clips = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).gradeAudioAndParse(anyString(), userPrompt.capture(), clips.capture(), any());
        assertEquals("Answer to question 1: Q1?", clips.getValue().get(0).label());
        assertSame(b, clips.getValue().get(1).audio());
        assertTrue(userPrompt.getValue().contains("2. Q2? (answer: 15 seconds)"));
    }

    @Test
    @DisplayName("rejects a response without a numeric band or without feedback, so it is retried")
    void rejectsIncomplete() {
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"pronunciation\": 7.2", "\"pronunciation\": \"good\""), 0));
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"summary\": \"A clear answer.\"", "\"summary\": \"\""), 0));
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"strengths\": [\"Good range\"]", "\"strengths\": \"Good range\""), 0));
    }

    @Test
    @DisplayName("tells Gemini the part, the cue card points and the recording length")
    void userPrompt() {
        SpeakingPrompt cueCard = SpeakingPrompt.builder().part(2).topic("A journey")
                .questionText("Describe a journey you remember well.")
                .cuePoints("where you went\nwho you went with").build();

        String prompt = SpeakingGradingService.buildUserPrompt(cueCard, List.of("Describe a journey you remember well."),
                List.of(new SpeakingGradingService.Recording(new byte[1], "audio/webm", 95)));

        assertTrue(prompt.contains("Part 2"));
        assertTrue(prompt.contains("- who you went with"));
        assertTrue(prompt.contains("95 seconds"));
    }
}
