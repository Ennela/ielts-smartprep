package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.model.entity.SpeakingPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Parsing and banding of Gemini's Speaking grade; no network. */
class SpeakingGradingServiceTest {

    private final SpeakingGradingService service =
            new SpeakingGradingService(mock(GeminiClient.class), new ObjectMapper());

    private static final String GOOD = """
            {"transcript": "I come from Hue.", "fluencyCoherence": 6.5, "lexicalResource": "6",
             "grammaticalRange": 5.5, "pronunciation": 7.2,
             "summary": "A clear answer.", "strengths": ["Good range"], "improvements": ["Extend answers"],
             "criteria": {"fluencyCoherence": "Few pauses.", "pronunciation": "Clear."}}
            """;

    @Test
    @DisplayName("rounds each band to a half and the overall with IELTS rounding")
    void bands() throws Exception {
        SpeakingGradingService.Result r = service.toResult(service.parseAndValidate(GOOD));

        assertEquals(new BigDecimal("6.5"), r.getFluencyBand());
        assertEquals(new BigDecimal("6.0"), r.getLexicalBand());
        assertEquals(new BigDecimal("5.5"), r.getGrammarBand());
        assertEquals(new BigDecimal("7.0"), r.getPronunciationBand());
        // (6.5 + 6.0 + 5.5 + 7.0) / 4 = 6.25 -> 6.5
        assertEquals(new BigDecimal("6.5"), r.getOverallBand());
        assertEquals("I come from Hue.", r.getTranscript());
        assertEquals(2, r.getCriteriaComments().size());
    }

    @Test
    @DisplayName("rejects a response without a numeric band or without feedback, so it is retried")
    void rejectsIncomplete() {
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"pronunciation\": 7.2", "\"pronunciation\": \"good\"")));
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"summary\": \"A clear answer.\"", "\"summary\": \"\"")));
        assertThrows(InvalidAiResponseException.class, () -> service.parseAndValidate(
                GOOD.replace("\"strengths\": [\"Good range\"]", "\"strengths\": \"Good range\"")));
    }

    @Test
    @DisplayName("tells Gemini the part, the cue card points and the recording length")
    void userPrompt() {
        SpeakingPrompt cueCard = SpeakingPrompt.builder().part(2).topic("A journey")
                .questionText("Describe a journey you remember well.")
                .cuePoints("where you went\nwho you went with").build();

        String prompt = SpeakingGradingService.buildUserPrompt(cueCard, 95);

        assertTrue(prompt.contains("Part 2"));
        assertTrue(prompt.contains("- who you went with"));
        assertTrue(prompt.contains("95 seconds"));
    }
}
