package com.smartprep.service.vocab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.VocabInsight;
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
    private final VocabInsightValidator insightValidator;

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

    /**
     * The contract for a context-aware explanation.
     *
     * <p>It is deliberately more prescriptive about what NOT to produce than about what to
     * produce. Left to itself a model pads: five near-identical examples, a synonym list
     * for a word that has no interesting synonym, a "formal / academic" label on anything
     * long. Each of those is a learner reading more and understanding less, so each has an
     * explicit prohibition below.
     */
    private static final String INSIGHT_SYSTEM_PROMPT = """
            You are a lexicographer and an English teacher who has taught Vietnamese university
            students preparing for IELTS (target band 6.0-7.0) for many years.

            Your job is NOT to translate a word. It is to explain WHEN, WHY and HOW it is used,
            so that the learner can choose it correctly instead of choosing a near-synonym.

            Vietnamese explanation fields (names ending in "Vi") must be written in natural
            Vietnamese. English fields (word, definitionEn, example sentences, patterns) must be
            in English. Never mix the two inside one field.

            OUTPUT — return ONLY this JSON object, with no code fences and no commentary:
            {
              "word": "trust",
              "partOfSpeech": "verb",
              "ipa": "/trʌst/",
              "coreMeaningVi": "Tin tưởng vào sự đáng tin cậy của ai hoặc điều gì.",
              "definitionEn": "To believe that someone or something is reliable or honest.",
              "registers": ["neutral"],
              "registerNoteVi": "Trung tính trong hầu hết ngữ cảnh; ...",
              "contextSummaryVi": "Người nói muốn nhấn mạnh rằng họ dựa vào được vào ai đó.",
              "senses": [
                {
                  "meaningVi": "...",
                  "definitionEn": "...",
                  "contextVi": "Tình huống nào làm nảy sinh cách nói này và nó hàm ý điều gì.",
                  "registers": ["neutral"],
                  "examples": [
                    {
                      "english": "I trust him to keep his promises.",
                      "vietnamese": "Tôi tin anh ấy sẽ giữ lời hứa.",
                      "whyVi": "Vì sao chọn từ này ở câu này, sắc thái nào mất đi nếu dịch sát nghĩa.",
                      "setting": "EVERYDAY"
                    }
                  ]
                }
              ],
              "synonymComparisons": [
                {
                  "focus": "trust vs faith vs belief",
                  "words": [
                    {
                      "word": "trust",
                      "coreIdeaVi": "...",
                      "typicalContextVi": "...",
                      "nuanceVi": "...",
                      "registers": ["neutral"]
                    }
                  ],
                  "contrastExamples": [
                    { "english": "...", "vietnamese": "...", "whyVi": "...", "setting": "EVERYDAY" }
                  ],
                  "interchangeabilityVi": "Khi nào thay thế được và nghĩa đổi ra sao."
                }
              ],
              "collocations": [ { "phrase": "...", "meaningVi": "...", "example": "..." } ],
              "grammarPatterns": [ { "pattern": "...", "explanationVi": "...", "example": "..." } ],
              "commonMistakes": [
                { "incorrect": "...", "corrected": "...", "whyVi": "...", "type": "UNNATURAL" }
              ],
              "ieltsGuidance": { "speakingVi": "...", "writingVi": "...", "cautionVi": "..." }
            }

            RULES

            senses (1-4, required)
            - Cover only the meanings that matter for this item in its learning context. Do not
              collapse distinct meanings into one vague Vietnamese gloss, and do not list every
              dictionary sense either.
            - contextVi must be specific. "Dùng trong nhiều tình huống" is a failure. Say what
              situation produces the expression and what attitude, certainty, politeness,
              emphasis or emotion it carries.

            examples (1-3 per sense)
            - Realistic sentences a learner could actually hear or say. No textbook filler.
            - Never two sentences that demonstrate the same thing. Fewer, distinct examples.
            - setting is EVERYDAY, IELTS_SPEAKING or IELTS_WRITING. Vary it where the word is
              genuinely used across those contexts; omit it if unsure.

            registers
            - Only these labels: informal, neutral, formal, academic, slang, literary.
            - Use several when the register genuinely changes with the meaning, and say why in
              registerNoteVi. Do not stamp one label on a word whose register shifts.
            - An advanced or rare word is NOT automatically formal or academic. Do not encourage
              complicated wording for its own sake.

            synonymComparisons (0-2, omit when there is nothing worth saying)
            - Only near-synonyms a learner would actually confuse. Never a bare list.
            - Every compared word needs its own core idea, typical context and nuance, and at
              least two words per comparison.
            - contrastExamples must show the difference, e.g. why "I trust him" and "I have faith
              in him" are both natural but do not say the same thing.
            - If no comparison is genuinely useful, return an empty array. Do not invent one.

            collocations and grammarPatterns (omit what does not apply)
            - Only patterns this word actually appears in: verb+noun, adjective+noun, the
              preposition that follows it, countable or uncountable, transitive or intransitive.
            - Where the grammar changes the meaning, that IS the explanation. For "chance",
              "a chance to do sth", "a chance of doing sth", "take a chance" and "give sb a
              chance" mean different things, and "a chance" versus "the chance" depends on
              whether the speaker introduces an opportunity or points at a specific known one.
              Explain that inside this item, not as a separate grammar lesson.
            - Apply the same treatment to articles, modals, phrasal verbs, idioms and discourse
              markers whenever the surrounding context decides the meaning.

            commonMistakes (0-4, omit when there is no real mistake)
            - Real errors Vietnamese learners make: word-for-word translation from Vietnamese,
              wrong preposition, unnatural collocation, a synonym in the wrong context, formal
              and informal confused, a general meaning used where a context-specific one belongs.
            - type says which kind of problem it is:
              GRAMMAR    = ungrammatical English
              UNNATURAL  = grammatical but no native speaker says it
              REGISTER   = correct English, wrong level of formality for the situation
              CONTEXT    = correct English, wrong sense for this situation
            - Regional or stylistic variation is not a mistake. Do not list one.
            - Do not invent a mistake to fill the section.

            ieltsGuidance
            - Say where the word is natural: Speaking, Writing Task 1, Writing Task 2, describing
              experience, giving an opinion, comparing, arguing. Say when a simpler word is the
              better choice.
            - cautionVi is for the risk of sounding memorised, forced, too casual or wrong for
              the task.
            - NEVER claim a word raises a band score. Rare vocabulary is not good vocabulary.
              Accuracy, naturalness and appropriateness are what matter.

            HONESTY
            - If you are not confident about a register, a pronunciation or a nuance, omit that
              field rather than stating it as fact. Hedge inside Vietnamese explanations where
              usage genuinely varies.
            - Keep every explanation short enough to read on a phone. Do not repeat the same
              point in two sections.
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

    /**
     * Generate the context-aware explanation for one word.
     *
     * <p>Parsing and validation happen inside {@link GeminiClient#generateAndParse}, so a
     * payload that arrives malformed or empty is retried rather than stored. What comes back
     * has already passed {@link VocabInsightValidator}.
     *
     * @param word          the saved word or expression
     * @param partOfSpeech  the saved part of speech, when the learner recorded one
     * @param contextHint   the sentence or source the word was collected from; it decides
     *                      which senses are worth explaining, so the explanation for a word
     *                      met in a reading passage is not the same as a bare dictionary entry
     * @throws com.smartprep.exception.AiServiceException        when the model cannot be reached
     * @throws InvalidAiResponseException                        when nothing usable was returned
     */
    public VocabInsight generateInsight(String word, String partOfSpeech, String contextHint) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Word is required to generate an explanation");
        }

        StringBuilder userPrompt = new StringBuilder("Explain this vocabulary item: ").append(word.trim());
        if (partOfSpeech != null && !partOfSpeech.isBlank()) {
            userPrompt.append("\nPart of speech recorded by the learner: ").append(partOfSpeech.trim());
        }
        if (contextHint != null && !contextHint.isBlank()) {
            userPrompt.append("\nThe learner met it in this context: ").append(contextHint.trim())
                    .append("\nExplain the sense that fits this context first. Mention another important ")
                    .append("sense only if the learner is likely to meet it.");
        }

        return geminiClient.generateAndParse(
                INSIGHT_SYSTEM_PROMPT,
                userPrompt.toString(),
                response -> {
                    VocabInsight parsed;
                    try {
                        parsed = objectMapper.readValue(cleanResponseText(response), VocabInsight.class);
                    } catch (Exception e) {
                        log.warn("Failed to parse vocabulary explanation for '{}' ({})",
                                word, e.getClass().getSimpleName());
                        throw new InvalidAiResponseException(
                                "AI returned invalid JSON for the vocabulary explanation: " + e.getMessage());
                    }
                    return insightValidator.validate(parsed, word);
                });
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
