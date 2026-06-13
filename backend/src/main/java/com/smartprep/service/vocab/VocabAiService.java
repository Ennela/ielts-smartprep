package com.smartprep.service.vocab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.service.ai.GeminiClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabAiService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuggestedVocab {
        private String word;
        private String phonetic;
        private String partOfSpeech;
        private String meaningVi;
        private String example;
        private String cefrLevel;
        private String collocation;
    }

    private static final String SYSTEM_PROMPT = """
            You are an expert IELTS vocabulary tutor and lexicographer.
            Analyze the provided IELTS source text (reading passage, listening transcript, essay prompt, or transcript) and extract advanced vocabulary items (CEFR levels B2, C1, C2) that are useful for students to learn.

            For each vocabulary item, provide:
            1. The word or phrase itself.
            2. Phonetic spelling (e.g. /jūˈbikwədəs/).
            3. Part of speech (noun, verb, adjective, adverb, phrase, etc.).
            4. Meaning in Vietnamese (accurate and natural translation).
            5. An example sentence (either from the text or a new illustrative sentence).
            6. CEFR level (B2, C1, or C2).
            7. Common collocation or expression using this word.

            You MUST output a valid JSON array of objects with the following exact fields:
            [
              {
                "word": "ubiquitous",
                "phonetic": "/juːˈbɪkwɪtəs/",
                "partOfSpeech": "adjective",
                "meaningVi": "phổ biến, có mặt ở khắp nơi",
                "example": "Mobile phones are ubiquitous in modern society.",
                "cefrLevel": "C1",
                "collocation": "ubiquitous presence"
              }
            ]

            Return ONLY the valid JSON array. No markdown code fences, no extra text, no HTML tags.
            Extract advanced vocabulary items from the source text.
            Choose roughly 1 item per 40–60 words of source, with a minimum of 12.
            Do not impose an upper limit; cover all genuinely useful advanced items.
            Return as a JSON array.
            """;

    public List<SuggestedVocab> suggestVocabulary(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return Collections.emptyList();
        }

        String userPrompt = "Source text to analyze:\n\n" + sourceText;

        try {
            return geminiClient.generateAndParse(
                    SYSTEM_PROMPT,
                    userPrompt,
                    response -> {
                        String cleanResponse = cleanResponseText(response);
                        try {
                            return objectMapper.readValue(cleanResponse, new TypeReference<List<SuggestedVocab>>() {
                            });
                        } catch (Exception e) {
                            log.error("Failed to parse JSON response: {}", response, e);
                            throw new InvalidAiResponseException(
                                    "AI returned invalid JSON array for vocabulary: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error in AI vocabulary suggestion generation: ", e);
            return Collections.emptyList();
        }
    }

    private String cleanResponseText(String response) {
        if (response == null)
            return "[]";
        String clean = response.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }
}
