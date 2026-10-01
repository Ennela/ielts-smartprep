package com.smartprep.service.util;

import com.smartprep.model.enums.QuestionType;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public class IeltsScoringUtils {

    private static final Map<Integer, BigDecimal> LISTENING_BAND_MAP = new HashMap<>();
    private static final Map<Integer, BigDecimal> READING_BAND_MAP = new HashMap<>();
    private static final Map<Integer, BigDecimal> READING_GT_BAND_MAP = new HashMap<>();

    static {
        // Listening Band Map (out of 40)
        LISTENING_BAND_MAP.put(40, new BigDecimal("9.0"));
        LISTENING_BAND_MAP.put(39, new BigDecimal("9.0"));
        LISTENING_BAND_MAP.put(38, new BigDecimal("8.5"));
        LISTENING_BAND_MAP.put(37, new BigDecimal("8.5"));
        LISTENING_BAND_MAP.put(36, new BigDecimal("8.0"));
        LISTENING_BAND_MAP.put(35, new BigDecimal("8.0"));
        LISTENING_BAND_MAP.put(34, new BigDecimal("7.5"));
        LISTENING_BAND_MAP.put(33, new BigDecimal("7.5"));
        LISTENING_BAND_MAP.put(32, new BigDecimal("7.5"));
        LISTENING_BAND_MAP.put(31, new BigDecimal("7.0"));
        LISTENING_BAND_MAP.put(30, new BigDecimal("7.0"));
        LISTENING_BAND_MAP.put(29, new BigDecimal("6.5"));
        LISTENING_BAND_MAP.put(28, new BigDecimal("6.5"));
        LISTENING_BAND_MAP.put(27, new BigDecimal("6.5"));
        LISTENING_BAND_MAP.put(26, new BigDecimal("6.5"));
        LISTENING_BAND_MAP.put(25, new BigDecimal("6.0"));
        LISTENING_BAND_MAP.put(24, new BigDecimal("6.0"));
        LISTENING_BAND_MAP.put(23, new BigDecimal("6.0"));
        LISTENING_BAND_MAP.put(22, new BigDecimal("5.5"));
        LISTENING_BAND_MAP.put(21, new BigDecimal("5.5"));
        LISTENING_BAND_MAP.put(20, new BigDecimal("5.5"));
        LISTENING_BAND_MAP.put(19, new BigDecimal("5.0"));
        LISTENING_BAND_MAP.put(18, new BigDecimal("5.0"));
        LISTENING_BAND_MAP.put(17, new BigDecimal("5.0"));
        LISTENING_BAND_MAP.put(16, new BigDecimal("5.0"));
        LISTENING_BAND_MAP.put(15, new BigDecimal("4.5"));
        LISTENING_BAND_MAP.put(14, new BigDecimal("4.5"));
        LISTENING_BAND_MAP.put(13, new BigDecimal("4.5"));
        LISTENING_BAND_MAP.put(12, new BigDecimal("4.0"));
        LISTENING_BAND_MAP.put(11, new BigDecimal("4.0"));
        LISTENING_BAND_MAP.put(10, new BigDecimal("4.0"));
        LISTENING_BAND_MAP.put(9, new BigDecimal("3.5"));
        LISTENING_BAND_MAP.put(8, new BigDecimal("3.5"));
        LISTENING_BAND_MAP.put(7, new BigDecimal("3.5"));
        LISTENING_BAND_MAP.put(6, new BigDecimal("3.5"));
        LISTENING_BAND_MAP.put(5, new BigDecimal("3.0"));
        LISTENING_BAND_MAP.put(4, new BigDecimal("3.0"));
        LISTENING_BAND_MAP.put(3, new BigDecimal("2.5"));
        LISTENING_BAND_MAP.put(2, new BigDecimal("2.0"));
        LISTENING_BAND_MAP.put(1, new BigDecimal("1.0"));
        LISTENING_BAND_MAP.put(0, new BigDecimal("1.0"));

        // Reading Academic Band Map (out of 40)
        READING_BAND_MAP.put(40, new BigDecimal("9.0"));
        READING_BAND_MAP.put(39, new BigDecimal("9.0"));
        READING_BAND_MAP.put(38, new BigDecimal("8.5"));
        READING_BAND_MAP.put(37, new BigDecimal("8.5"));
        READING_BAND_MAP.put(36, new BigDecimal("8.0"));
        READING_BAND_MAP.put(35, new BigDecimal("8.0"));
        READING_BAND_MAP.put(34, new BigDecimal("7.5"));
        READING_BAND_MAP.put(33, new BigDecimal("7.5"));
        READING_BAND_MAP.put(32, new BigDecimal("7.0"));
        READING_BAND_MAP.put(31, new BigDecimal("7.0"));
        READING_BAND_MAP.put(30, new BigDecimal("7.0"));
        READING_BAND_MAP.put(29, new BigDecimal("6.5"));
        READING_BAND_MAP.put(28, new BigDecimal("6.5"));
        READING_BAND_MAP.put(27, new BigDecimal("6.5"));
        READING_BAND_MAP.put(26, new BigDecimal("6.0"));
        READING_BAND_MAP.put(25, new BigDecimal("6.0"));
        READING_BAND_MAP.put(24, new BigDecimal("6.0"));
        READING_BAND_MAP.put(23, new BigDecimal("6.0"));
        READING_BAND_MAP.put(22, new BigDecimal("5.5"));
        READING_BAND_MAP.put(21, new BigDecimal("5.5"));
        READING_BAND_MAP.put(20, new BigDecimal("5.5"));
        READING_BAND_MAP.put(19, new BigDecimal("5.5"));
        READING_BAND_MAP.put(18, new BigDecimal("5.0"));
        READING_BAND_MAP.put(17, new BigDecimal("5.0"));
        READING_BAND_MAP.put(16, new BigDecimal("5.0"));
        READING_BAND_MAP.put(15, new BigDecimal("5.0"));
        READING_BAND_MAP.put(14, new BigDecimal("4.5"));
        READING_BAND_MAP.put(13, new BigDecimal("4.5"));
        READING_BAND_MAP.put(12, new BigDecimal("4.0"));
        READING_BAND_MAP.put(11, new BigDecimal("4.0"));
        READING_BAND_MAP.put(10, new BigDecimal("4.0"));
        READING_BAND_MAP.put(9, new BigDecimal("3.5"));
        READING_BAND_MAP.put(8, new BigDecimal("3.5"));
        READING_BAND_MAP.put(7, new BigDecimal("3.0"));
        READING_BAND_MAP.put(6, new BigDecimal("3.0"));
        READING_BAND_MAP.put(5, new BigDecimal("2.5"));
        READING_BAND_MAP.put(4, new BigDecimal("2.5"));
        READING_BAND_MAP.put(3, new BigDecimal("2.0"));
        READING_BAND_MAP.put(2, new BigDecimal("2.0"));
        READING_BAND_MAP.put(1, new BigDecimal("1.0"));
        READING_BAND_MAP.put(0, new BigDecimal("1.0"));

        // Reading General Training Band Map (out of 40)
        READING_GT_BAND_MAP.put(40, new BigDecimal("9.0"));
        READING_GT_BAND_MAP.put(39, new BigDecimal("8.5"));
        READING_GT_BAND_MAP.put(38, new BigDecimal("8.0"));
        READING_GT_BAND_MAP.put(37, new BigDecimal("8.0"));
        READING_GT_BAND_MAP.put(36, new BigDecimal("7.5"));
        READING_GT_BAND_MAP.put(35, new BigDecimal("7.0"));
        READING_GT_BAND_MAP.put(34, new BigDecimal("7.0"));
        READING_GT_BAND_MAP.put(33, new BigDecimal("6.5"));
        READING_GT_BAND_MAP.put(32, new BigDecimal("6.5"));
        READING_GT_BAND_MAP.put(31, new BigDecimal("6.0"));
        READING_GT_BAND_MAP.put(30, new BigDecimal("6.0"));
        READING_GT_BAND_MAP.put(29, new BigDecimal("5.5"));
        READING_GT_BAND_MAP.put(28, new BigDecimal("5.5"));
        READING_GT_BAND_MAP.put(27, new BigDecimal("5.5"));
        READING_GT_BAND_MAP.put(26, new BigDecimal("5.0"));
        READING_GT_BAND_MAP.put(25, new BigDecimal("5.0"));
        READING_GT_BAND_MAP.put(24, new BigDecimal("5.0"));
        READING_GT_BAND_MAP.put(23, new BigDecimal("5.0"));
        READING_GT_BAND_MAP.put(22, new BigDecimal("4.5"));
        READING_GT_BAND_MAP.put(21, new BigDecimal("4.5"));
        READING_GT_BAND_MAP.put(20, new BigDecimal("4.5"));
        READING_GT_BAND_MAP.put(19, new BigDecimal("4.5"));
        READING_GT_BAND_MAP.put(18, new BigDecimal("4.0"));
        READING_GT_BAND_MAP.put(17, new BigDecimal("4.0"));
        READING_GT_BAND_MAP.put(16, new BigDecimal("4.0"));
        READING_GT_BAND_MAP.put(15, new BigDecimal("4.0"));
        READING_GT_BAND_MAP.put(14, new BigDecimal("3.5"));
        READING_GT_BAND_MAP.put(13, new BigDecimal("3.5"));
        READING_GT_BAND_MAP.put(12, new BigDecimal("3.5"));
        READING_GT_BAND_MAP.put(11, new BigDecimal("3.0"));
        READING_GT_BAND_MAP.put(10, new BigDecimal("3.0"));
        READING_GT_BAND_MAP.put(9, new BigDecimal("3.0"));
        READING_GT_BAND_MAP.put(8, new BigDecimal("2.5"));
        READING_GT_BAND_MAP.put(7, new BigDecimal("2.5"));
        READING_GT_BAND_MAP.put(6, new BigDecimal("2.5"));
        READING_GT_BAND_MAP.put(5, new BigDecimal("2.0"));
        READING_GT_BAND_MAP.put(4, new BigDecimal("2.0"));
        READING_GT_BAND_MAP.put(3, new BigDecimal("2.0"));
        READING_GT_BAND_MAP.put(2, new BigDecimal("1.5"));
        READING_GT_BAND_MAP.put(1, new BigDecimal("1.0"));
        READING_GT_BAND_MAP.put(0, new BigDecimal("1.0"));
    }

    /**
     * Calculate IELTS Listening Band Score (out of 40)
     */
    public static BigDecimal calculateListeningBand(int correctCount) {
        int correct = Math.min(Math.max(correctCount, 0), 40);
        return LISTENING_BAND_MAP.getOrDefault(correct, new BigDecimal("1.0"));
    }

    /**
     * Calculate IELTS Academic Reading Band Score (out of 40)
     */
    public static BigDecimal calculateReadingBand(int correctCount) {
        return calculateReadingBand(correctCount, "ACADEMIC");
    }

    /**
     * Calculate IELTS Reading Band Score (Academic or General Training, out of 40)
     */
    public static BigDecimal calculateReadingBand(int correctCount, String moduleType) {
        int correct = Math.min(Math.max(correctCount, 0), 40);
        if ("GENERAL_TRAINING".equalsIgnoreCase(moduleType)) {
            return READING_GT_BAND_MAP.getOrDefault(correct, new BigDecimal("1.0"));
        }
        return READING_BAND_MAP.getOrDefault(correct, new BigDecimal("1.0"));
    }

    /**
     * Scale a raw score from a shorter paper onto the official 40-question scale.
     * <p>
     * Practice papers are rarely 40 questions — a single reading passage is 13, a single
     * listening part is 10. Converting the proportion first is what lets every path share
     * one band table instead of inventing a second, shorter one.
     */
    private static int scaleToFortyQuestions(int correctCount, int totalQuestions) {
        if (totalQuestions <= 0) return 0;
        int bounded = Math.min(Math.max(correctCount, 0), totalQuestions);
        if (totalQuestions == 40) return bounded;
        return (int) Math.round(((double) bounded / totalQuestions) * 40.0);
    }

    /**
     * Calculate IELTS Listening Band Score for a paper of any length.
     * The raw score is scaled onto the 40-question table first.
     */
    public static BigDecimal calculateListeningBand(int correctCount, int totalQuestions) {
        return calculateListeningBand(scaleToFortyQuestions(correctCount, totalQuestions));
    }

    /**
     * Calculate IELTS Reading Band Score for a paper of any length.
     * The raw score is scaled onto the 40-question table first.
     */
    public static BigDecimal calculateReadingBand(int correctCount, int totalQuestions, String moduleType) {
        return calculateReadingBand(scaleToFortyQuestions(correctCount, totalQuestions), moduleType);
    }

    /**
     * Check if a Listening answer matches correctly.
     *
     * <p>MCQ answers are letters and compare exactly. Everything else is a written answer
     * and goes through {@link #writtenAnswerMatches}, the same comparison Reading uses.
     */
    public static boolean isListeningCorrect(String correct, String userAnswer, String questionType) {
        return isListeningCorrect(correct, userAnswer, questionType, null);
    }

    /**
     * Check a Listening answer against the question's word limit as well.
     *
     * <p>Listening uses Reading's question types -- a matching group answers with a letter, a
     * form or notes group with words -- and they are marked the same way, so this is the
     * Reading rule. Before V54 a Listening question could only be MCQ or a gap, and a letter
     * answer to a matching question would have been compared as words.
     */
    public static boolean isListeningCorrect(String correct, String userAnswer, String questionType,
                                             Integer wordLimit) {
        return isReadingCorrect(QuestionType.valueOf(questionType), correct, userAnswer, wordLimit);
    }

    /**
     * Check if a Reading answer matches correctly, with no word limit applied.
     */
    public static boolean isReadingCorrect(QuestionType questionType, String correctAnswer, String userAnswer) {
        return isReadingCorrect(questionType, correctAnswer, userAnswer, null);
    }

    /**
     * Check if a Reading answer matches correctly.
     *
     * @param wordLimit the question's "NO MORE THAN N WORDS" limit, or null when it has none.
     *                  Only completion questions are limited; an answer over the limit is
     *                  wrong even when its words are right, as it is in the exam.
     */
    public static boolean isReadingCorrect(QuestionType questionType, String correctAnswer, String userAnswer,
                                           Integer wordLimit) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String correct = correctAnswer.trim();
        String answer = userAnswer.trim();

        return switch (questionType) {
            case TFNG, YNNG ->
                correct.equalsIgnoreCase(answer);

            case MCQ, MATCHING_INFORMATION, MATCHING_FEATURES, MATCHING_SENTENCE_ENDINGS ->
                correct.equalsIgnoreCase(answer);

            case MATCHING_HEADINGS ->
                correct.equalsIgnoreCase(answer);

            case SENTENCE_COMPLETION, SUMMARY_COMPLETION, FILL_BLANK, DIAGRAM_LABEL_COMPLETION, SHORT_ANSWER ->
                !exceedsWordLimit(answer, wordLimit) && writtenAnswerMatches(correct, answer);
        };
    }

    // ── Written answers ──────────────────────────────────────────────────────
    //
    // Before this, a written answer matched only if it was character-for-character the
    // stored one after lower-casing and dropping a leading article. So "paint." was wrong
    // for "paint", "85" was wrong for "eighty-five", "coal" was wrong for "coal / charcoal",
    // and an answer five words long passed a "NO MORE THAN TWO WORDS" question. Listening
    // and Reading also normalised differently for the same kind of question.

    /** Separates alternatives an examiner would accept: "coal / charcoal", "coal|charcoal". */
    private static final Pattern ALTERNATIVE_SEPARATOR = Pattern.compile("\\s*[/|]\\s*");

    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
            Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14),
            Map.entry("fifteen", 15), Map.entry("sixteen", 16), Map.entry("seventeen", 17),
            Map.entry("eighteen", 18), Map.entry("nineteen", 19), Map.entry("twenty", 20),
            Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50),
            Map.entry("sixty", 60), Map.entry("seventy", 70), Map.entry("eighty", 80),
            Map.entry("ninety", 90));

    private static final Map<String, Integer> NUMBER_SCALES = Map.of(
            "hundred", 100, "thousand", 1_000, "million", 1_000_000);

    /** The pairs examiners accept either way round. Applied to whole words only. */
    private static final Map<String, String> BRITISH_TO_AMERICAN = Map.of(
            "organisation", "organization",
            "centre", "center",
            "colour", "color",
            "favour", "favor",
            "behaviour", "behavior",
            "travelling", "traveling",
            "cancelled", "canceled");

    /**
     * Whether a written answer is one of the forms the stored answer accepts.
     *
     * <p>Package-visible so the rules can be tested directly.
     */
    static boolean writtenAnswerMatches(String correct, String answer) {
        String normalizedAnswer = normalizeWrittenAnswer(answer);
        if (normalizedAnswer.isEmpty()) return false;
        for (String form : acceptedForms(correct)) {
            if (normalizeWrittenAnswer(form).equals(normalizedAnswer)) return true;
        }
        return false;
    }

    /**
     * The forms a stored answer accepts. "coal / charcoal" accepts either word; "24/7" and
     * "1/2" stay whole, because a slash between numbers is part of the answer rather than a
     * separator between two of them.
     */
    static List<String> acceptedForms(String correct) {
        String whole = correct.trim();
        String[] parts = ALTERNATIVE_SEPARATOR.split(whole);
        if (parts.length < 2) return List.of(whole);
        for (String part : parts) {
            if (part.isBlank() || !HAS_LETTER.matcher(part).find()) return List.of(whole);
        }
        return List.of(parts);
    }

    /**
     * Whether an answer uses more words than the question allows.
     *
     * <p>Numbers do not count, because IELTS limits read "N words and/or a number", and a
     * leading article does not count either, because {@link #normalizeWrittenAnswer} drops it
     * before comparing. That is more lenient than an examiner on the article; the stored
     * answers often include one, and marking "the station" wrong for "station" would be the
     * worse mistake.
     */
    static boolean exceedsWordLimit(String answer, Integer wordLimit) {
        if (wordLimit == null || wordLimit <= 0 || answer == null) return false;
        String[] tokens = answer.toLowerCase(Locale.ROOT).trim()
                .replaceAll("^(the|a|an)\\s+", "")
                .split("\\s+");
        long words = 0;
        for (String token : tokens) {
            String bare = token.replaceAll("[^\\p{L}\\p{N}-]", "");
            if (bare.isEmpty() || isNumberToken(bare)) continue;
            words++;
        }
        return words > wordLimit;
    }

    /** "85", "12,000", "twenty-five", "thousand". */
    private static boolean isNumberToken(String token) {
        if (token.replace(",", "").matches("\\d+")) return true;
        for (String piece : token.split("-")) {
            if (!NUMBER_WORDS.containsKey(piece) && !NUMBER_SCALES.containsKey(piece) && !"and".equals(piece)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reduce a written answer to the form two acceptable answers share.
     *
     * <p>Lower case; thousands separators and apostrophes removed; hyphens and other
     * punctuation become spaces; a leading article dropped; runs of number words become
     * digits; British spellings become American. Both sides of a comparison go through
     * this, so each rule only has to make the two forms agree, not be linguistically exact.
     */
    static String normalizeWrittenAnswer(String text) {
        if (text == null) return "";
        String s = text.toLowerCase(Locale.ROOT)
                .replaceAll("(?<=\\d),(?=\\d{3})", "")
                .replace("'", "")
                .replace("\u2019", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("^(the|a|an) ", "");
        s = numberWordsToDigits(s);

        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(BRITISH_TO_AMERICAN.getOrDefault(word, word));
        }
        return out.toString();
    }

    /**
     * Replace each run of English number words with its value: "twelve thousand" becomes
     * "12000", "one hundred and five" becomes "105". Words that are not part of a number are
     * left alone, so "three bedrooms" becomes "3 bedrooms".
     */
    private static String numberWordsToDigits(String s) {
        String[] words = s.split(" ");
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            if (!NUMBER_WORDS.containsKey(words[i]) && !NUMBER_SCALES.containsKey(words[i])) {
                append(out, words[i]);
                i++;
                continue;
            }
            long total = 0;
            long current = 0;
            int j = i;
            while (j < words.length) {
                String w = words[j];
                if (NUMBER_WORDS.containsKey(w)) {
                    current += NUMBER_WORDS.get(w);
                } else if ("hundred".equals(w)) {
                    current = (current == 0 ? 1 : current) * 100;
                } else if (NUMBER_SCALES.containsKey(w)) {
                    total += (current == 0 ? 1 : current) * NUMBER_SCALES.get(w);
                    current = 0;
                } else if ("and".equals(w) && j + 1 < words.length && NUMBER_WORDS.containsKey(words[j + 1])) {
                    // "one hundred and five": the and belongs to the number.
                } else {
                    break;
                }
                j++;
            }
            append(out, Long.toString(total + current));
            i = j;
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String word) {
        if (out.length() > 0) out.append(' ');
        out.append(word);
    }

    /**
     * Standard IELTS overall band rounding:
     * - Average the component scores.
     * - If the fractional part of average is < 0.25, round down to .0
     * - If the fractional part is between 0.25 and 0.74, round to .5
     * - If the fractional part is >= 0.75, round up to next .0
     */
    public static BigDecimal roundOverallBand(BigDecimal average) {
        double avgDouble = average.doubleValue();
        int intPart = (int) avgDouble;
        double fracPart = avgDouble - intPart;

        double rounded;
        if (fracPart < 0.25) {
            rounded = intPart;
        } else if (fracPart < 0.75) {
            rounded = intPart + 0.5;
        } else {
            rounded = intPart + 1.0;
        }

        return BigDecimal.valueOf(rounded).setScale(1, RoundingMode.HALF_UP);
    }
}
