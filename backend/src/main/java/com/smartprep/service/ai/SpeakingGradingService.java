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
 *
 * Part 2 is one recording. Part 1 and Part 3 are one recording per question, sent together
 * in one call: the bands are for the whole set, plus a transcript and comment per answer.
 */
@Service
@RequiredArgsConstructor
public class SpeakingGradingService {

    static final String[] CRITERIA = {"fluencyCoherence", "lexicalResource", "grammaticalRange", "pronunciation"};

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /** One recording: the Part 2 answer, or the answer to one Part 1/3 question. */
    public record Recording(byte[] audio, String mimeType, int durationSeconds) {}

    /** What Gemini says about one Part 1/3 answer. */
    public record AnswerFeedback(String transcript, String comment) {}

    @Data
    @Builder
    public static class Result {
        /** Part 2 only; Part 1/3 transcripts are per answer. */
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
        /** Part 1/3: one per recording, in order. Empty for Part 2. */
        private List<AnswerFeedback> answers;
    }

    /**
     * @param questions  the prompt's questions, one per line of its text
     * @param recordings Part 2: exactly one; Part 1/3: one per question, in order
     */
    public Result grade(SpeakingPrompt prompt, List<String> questions, List<Recording> recordings) {
        boolean perQuestion = prompt.getPart() != 2;
        List<GeminiClient.AudioClip> clips = new ArrayList<>();
        for (int i = 0; i < recordings.size(); i++) {
            Recording r = recordings.get(i);
            String label = perQuestion ? "Answer to question " + (i + 1) + ": " + questions.get(i) : null;
            clips.add(new GeminiClient.AudioClip(label, r.audio(), r.mimeType()));
        }
        int expectedAnswers = perQuestion ? recordings.size() : 0;
        JsonNode json = geminiClient.gradeAudioAndParse(SYSTEM_PROMPT, buildUserPrompt(prompt, questions, recordings),
                clips, raw -> parseAndValidate(raw, expectedAnswers));
        return toResult(json, expectedAnswers);
    }

    static String buildUserPrompt(SpeakingPrompt prompt, List<String> questions, List<Recording> recordings) {
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
            sb.append("Recording length: ").append(recordings.get(0).durationSeconds()).append(" seconds.\n");
            sb.append("The attached audio is the candidate's answer. Grade it.");
        } else {
            sb.append("The examiner asked these questions one at a time; the candidate answered each straight away:\n");
            for (int i = 0; i < recordings.size(); i++) {
                sb.append(i + 1).append(". ").append(questions.get(i))
                        .append(" (answer: ").append(recordings.get(i).durationSeconds()).append(" seconds)\n");
            }
            sb.append("Each answer is a separate recording below, after the question it answers. ")
                    .append("Grade the answers together and comment on each one.");
        }
        return sb.toString();
    }

    JsonNode parseAndValidate(String raw, int expectedAnswers) throws Exception {
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
        if (expectedAnswers > 0 && (!json.path("answers").isArray() || json.path("answers").size() != expectedAnswers)) {
            throw new InvalidAiResponseException("Speaking grading response needs " + expectedAnswers + " answers");
        }
        return json;
    }

    Result toResult(JsonNode json, int expectedAnswers) {
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
                .answers(answers(json, expectedAnswers))
                .build();
    }

    private static List<AnswerFeedback> answers(JsonNode json, int expected) {
        List<AnswerFeedback> out = new ArrayList<>();
        for (int i = 0; i < expected; i++) {
            JsonNode a = json.path("answers").path(i);
            out.add(new AnswerFeedback(a.path("transcript").asText("").trim(), a.path("comment").asText("").trim()));
        }
        return out;
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
            - Part 1 and Part 3 come as one recording per question, in order, each after the question it
              answers. Grade the set as a whole: one set of bands for all the answers. Part 1 answers are
              naturally short (two to four sentences); do not mark down brevity there if the answer is
              relevant and a little extended. Part 3 answers should develop an opinion with reasons and examples.
            - Write feedback in clear, simple English addressed to the candidate ("you").
            - Transcripts are your best verbatim transcription of what was said, including obvious errors.
              Part 2: put it in "transcript" and return "answers" as []. Part 1 and Part 3: leave
              "transcript" empty and give one "answers" entry per recording, in the same order.

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
              },
              "answers": [
                {"transcript": "what was said in this answer", "comment": "One sentence: what worked, or one concrete fix."}
              ]
            }
            """;
}
