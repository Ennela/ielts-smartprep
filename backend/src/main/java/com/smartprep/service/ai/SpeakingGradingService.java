package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.model.entity.SpeakingPrompt;
import com.smartprep.service.util.IeltsScoringUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grades a recorded Speaking answer with Gemini, which listens to the audio itself:
 * pronunciation and fluency cannot be judged from a transcript.
 */
@Service
@RequiredArgsConstructor
public class SpeakingGradingService {

    static final String[] CRITERIA = {"fluencyCoherence", "lexicalResource", "grammaticalRange", "pronunciation"};

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class Result {
        private String transcript;
        private BigDecimal fluencyBand;
        private BigDecimal lexicalBand;
        private BigDecimal grammarBand;
        private BigDecimal pronunciationBand;
        private BigDecimal overallBand;
        private String summary;
        private List<String> strengths;
        private List<String> improvements;
        private Map<String, String> criteriaComments;
    }

    public Result grade(SpeakingPrompt prompt, byte[] audio, String mimeType, int durationSeconds) {
        String userPrompt = buildUserPrompt(prompt, durationSeconds);
        JsonNode json = geminiClient.gradeAudioAndParse(SYSTEM_PROMPT, userPrompt, audio, mimeType, this::parseAndValidate);
        return toResult(json);
    }

    static String buildUserPrompt(SpeakingPrompt prompt, int durationSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("IELTS Speaking Part ").append(prompt.getPart()).append(" - topic: ").append(prompt.getTopic()).append('\n');
        if (prompt.getPart() == 2) {
            sb.append("Cue card: ").append(prompt.getQuestionText()).append('\n');
            if (prompt.getCuePoints() != null) {
                sb.append("You should say:\n");
                for (String point : prompt.getCuePoints().split("\n")) {
                    sb.append("- ").append(point.trim()).append('\n');
                }
            }
        } else {
            sb.append("Questions the candidate was asked:\n");
            for (String q : prompt.getQuestionText().split("\n")) {
                sb.append("- ").append(q.trim()).append('\n');
            }
        }
        sb.append("Recording length: ").append(durationSeconds).append(" seconds.\n");
        sb.append("The attached audio is the candidate's answer. Grade it.");
        return sb.toString();
    }

    JsonNode parseAndValidate(String raw) throws Exception {
        JsonNode json = objectMapper.readTree(raw);
        if (json == null || !json.isObject()) {
            throw new InvalidAiResponseException("Speaking grading response is not a JSON object");
        }
        for (String field : CRITERIA) {
            JsonNode node = json.get(field);
            if (node == null || !(node.isNumber() || (node.isTextual() && node.asText().trim().matches("\\d+(\\.\\d+)?")))) {
                throw new InvalidAiResponseException("Speaking grading response has no numeric " + field);
            }
        }
        if (json.path("summary").asText("").isBlank()) {
            throw new InvalidAiResponseException("Speaking grading response missing summary");
        }
        if (!json.path("strengths").isArray() || !json.path("improvements").isArray()) {
            throw new InvalidAiResponseException("Speaking grading response missing strengths/improvements");
        }
        return json;
    }

    Result toResult(JsonNode json) {
        BigDecimal fc = band(json, "fluencyCoherence");
        BigDecimal lr = band(json, "lexicalResource");
        BigDecimal gra = band(json, "grammaticalRange");
        BigDecimal p = band(json, "pronunciation");
        BigDecimal avg = fc.add(lr).add(gra).add(p).divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP);

        Map<String, String> comments = new LinkedHashMap<>();
        for (String field : CRITERIA) {
            String comment = json.path("criteria").path(field).asText("");
            if (!comment.isBlank()) comments.put(field, comment);
        }
        return Result.builder()
                .transcript(json.path("transcript").asText(""))
                .fluencyBand(fc).lexicalBand(lr).grammarBand(gra).pronunciationBand(p)
                .overallBand(IeltsScoringUtils.roundOverallBand(avg))
                .summary(json.path("summary").asText())
                .strengths(texts(json.path("strengths")))
                .improvements(texts(json.path("improvements")))
                .criteriaComments(comments)
                .build();
    }

    private static BigDecimal band(JsonNode json, String field) {
        double score = json.path(field).asDouble();
        score = Math.max(0, Math.min(9, score));
        score = Math.round(score * 2) / 2.0;
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> {
            String t = n.asText("").trim();
            if (!t.isEmpty()) out.add(t);
        });
        return out;
    }

    private static final String SYSTEM_PROMPT = """
            You are a certified IELTS Speaking examiner. You will receive the task the candidate was
            given and an audio recording of their answer. Listen to the whole recording and grade it
            with the four official IELTS Speaking band descriptors:

            - fluencyCoherence: Fluency and Coherence (speed, hesitation, self-correction, linking, logical order)
            - lexicalResource: Lexical Resource (range, precision, collocation, paraphrase, idiomatic language)
            - grammaticalRange: Grammatical Range and Accuracy (range of structures, error frequency)
            - pronunciation: Pronunciation (intelligibility, word and sentence stress, intonation, individual sounds)

            Rules:
            - Each band is a multiple of 0.5 from 0 to 9. Judge only what you hear; do not reward length alone.
            - Judge pronunciation and fluency from the audio, never from the transcript.
            - If the recording is silent, too short to assess, not in English, or does not answer the task,
              give low bands (0-3) and say so plainly in the summary.
            - Part 2 answers should cover the cue card points and last close to two minutes; short or
              off-topic answers lose marks in fluencyCoherence.
            - Write feedback in clear, simple English addressed to the candidate ("you").
            - The transcript is your best verbatim transcription of what was said, including obvious errors.

            Return ONLY this JSON object:
            {
              "transcript": "what the candidate said",
              "fluencyCoherence": 6.5,
              "lexicalResource": 6.0,
              "grammaticalRange": 6.0,
              "pronunciation": 6.5,
              "summary": "Two or three sentences on the overall performance.",
              "strengths": ["specific strength", "specific strength"],
              "improvements": ["specific, actionable improvement with an example from the answer", "..."],
              "criteria": {
                "fluencyCoherence": "One or two sentences.",
                "lexicalResource": "One or two sentences.",
                "grammaticalRange": "One or two sentences.",
                "pronunciation": "One or two sentences."
              }
            }
            """;
}
