package com.smartprep.service.util;

import com.smartprep.model.enums.QuestionType;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

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
     * Check if a Listening answer matches correctly.
     */
    public static boolean isListeningCorrect(String correct, String userAnswer, String questionType) {
        if (userAnswer == null || userAnswer.isBlank()) return false;

        String cleanCorrect = correct.trim().toLowerCase();
        String cleanUser = userAnswer.trim().toLowerCase();

        if ("MCQ".equals(questionType)) {
            return cleanCorrect.equals(cleanUser);
        }

        // FILL_BLANK or standard: check direct match
        if (cleanCorrect.equals(cleanUser)) return true;

        // Check common spelling variations
        String normalizedCorrect = normalizeSpelling(cleanCorrect);
        String normalizedUser = normalizeSpelling(cleanUser);
        return normalizedCorrect.equals(normalizedUser);
    }

    /**
     * Check if a Reading answer matches correctly.
     */
    public static boolean isReadingCorrect(QuestionType questionType, String correctAnswer, String userAnswer) {
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

            case SENTENCE_COMPLETION, SUMMARY_COMPLETION, FILL_BLANK, DIAGRAM_LABEL_COMPLETION, SHORT_ANSWER -> {
                String normCorrect = normalizeCompletionText(correct);
                String normAnswer = normalizeCompletionText(answer);
                yield normCorrect.equals(normAnswer);
            }
        };
    }

    private static String normalizeSpelling(String s) {
        return s.replace("organisation", "organization")
                .replace("centre", "center")
                .replace("colour", "color")
                .replace("favour", "favor")
                .replace("behaviour", "behavior")
                .replace("travelling", "traveling")
                .replace("cancelled", "canceled")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeCompletionText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .replaceAll("^(the|a|an)\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
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
