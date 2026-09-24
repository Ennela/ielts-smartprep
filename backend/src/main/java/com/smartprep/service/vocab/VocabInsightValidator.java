package com.smartprep.service.vocab;

import com.smartprep.dto.response.VocabInsight;
import com.smartprep.exception.InvalidAiResponseException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Turns whatever the model returned into something the vocabulary page can render.
 *
 * <p>Three jobs, in order:
 * <ol>
 *   <li><b>Reject</b> a payload with no word or no core meaning. There is nothing to show,
 *       so another attempt is worth more than a half-empty card.</li>
 *   <li><b>Drop</b> blank entries, unknown register labels and duplicate examples. A model
 *       asked for "up to three examples" will pad, and three near-identical sentences teach
 *       less than one.</li>
 *   <li><b>Cap</b> every list and every string. A learner reading a card does not benefit
 *       from twelve collocations, and an unbounded payload is an unbounded row.</li>
 * </ol>
 *
 * <p>Everything below the core meaning is optional by design: a word with no useful
 * synonym distinction ends up with no comparison section, which is the correct outcome.
 */
@Component
public class VocabInsightValidator {

    private static final int MAX_SENSES = 4;
    private static final int MAX_EXAMPLES_PER_SENSE = 3;
    private static final int MAX_COMPARISONS = 2;
    private static final int MAX_COMPARED_WORDS = 4;
    private static final int MAX_CONTRAST_EXAMPLES = 3;
    private static final int MAX_COLLOCATIONS = 8;
    private static final int MAX_GRAMMAR_PATTERNS = 6;
    private static final int MAX_MISTAKES = 4;
    private static final int MAX_REGISTERS = 3;

    /** Long enough for a real explanation, short enough that no card becomes a wall of text. */
    private static final int MAX_TEXT = 600;
    private static final int MAX_SHORT_TEXT = 200;

    private static final Set<String> MISTAKE_TYPES = Set.of("GRAMMAR", "UNNATURAL", "REGISTER", "CONTEXT");
    private static final Set<String> SETTINGS = Set.of("EVERYDAY", "IELTS_SPEAKING", "IELTS_WRITING");

    /**
     * @param fallbackWord the saved word, used when the model echoed a different spelling
     * @throws InvalidAiResponseException when nothing usable survives, so the caller retries
     */
    public VocabInsight validate(VocabInsight raw, String fallbackWord) {
        if (raw == null) {
            throw new InvalidAiResponseException("AI returned no vocabulary explanation");
        }

        String word = trim(raw.getWord(), MAX_SHORT_TEXT);
        if (isBlank(word)) {
            word = trim(fallbackWord, MAX_SHORT_TEXT);
        }
        String coreMeaningVi = trim(raw.getCoreMeaningVi(), MAX_TEXT);
        if (isBlank(word) || isBlank(coreMeaningVi)) {
            throw new InvalidAiResponseException(
                    "AI explanation is missing the word or its core Vietnamese meaning");
        }

        List<VocabInsight.Sense> senses = senses(raw.getSenses());
        if (senses.isEmpty()) {
            throw new InvalidAiResponseException("AI explanation contains no usable sense");
        }

        return VocabInsight.builder()
                .word(word)
                .partOfSpeech(trim(raw.getPartOfSpeech(), MAX_SHORT_TEXT))
                .ipa(trim(raw.getIpa(), MAX_SHORT_TEXT))
                .coreMeaningVi(coreMeaningVi)
                .definitionEn(trim(raw.getDefinitionEn(), MAX_TEXT))
                .registers(registers(raw.getRegisters()))
                .registerNoteVi(trim(raw.getRegisterNoteVi(), MAX_TEXT))
                .contextSummaryVi(trim(raw.getContextSummaryVi(), MAX_TEXT))
                .senses(senses)
                .synonymComparisons(comparisons(raw.getSynonymComparisons()))
                .collocations(collocations(raw.getCollocations()))
                .grammarPatterns(grammarPatterns(raw.getGrammarPatterns()))
                .commonMistakes(mistakes(raw.getCommonMistakes()))
                .ieltsGuidance(guidance(raw.getIeltsGuidance()))
                .build();
    }

    private List<VocabInsight.Sense> senses(List<VocabInsight.Sense> raw) {
        // Examples are deduplicated across the whole insight, not per sense: a model that
        // reuses one sentence under two senses has taught the learner nothing twice.
        Set<String> seenExamples = new LinkedHashSet<>();
        List<VocabInsight.Sense> out = new ArrayList<>();
        for (VocabInsight.Sense sense : nullSafe(raw)) {
            if (sense == null) {
                continue;
            }
            String meaningVi = trim(sense.getMeaningVi(), MAX_TEXT);
            if (isBlank(meaningVi)) {
                continue;
            }
            out.add(VocabInsight.Sense.builder()
                    .meaningVi(meaningVi)
                    .definitionEn(trim(sense.getDefinitionEn(), MAX_TEXT))
                    .contextVi(trim(sense.getContextVi(), MAX_TEXT))
                    .registers(registers(sense.getRegisters()))
                    .examples(examples(sense.getExamples(), MAX_EXAMPLES_PER_SENSE, seenExamples))
                    .build());
            if (out.size() == MAX_SENSES) {
                break;
            }
        }
        return out;
    }

    private List<VocabInsight.Example> examples(List<VocabInsight.Example> raw, int limit, Set<String> seen) {
        List<VocabInsight.Example> out = new ArrayList<>();
        for (VocabInsight.Example example : nullSafe(raw)) {
            if (example == null) {
                continue;
            }
            String english = trim(example.getEnglish(), MAX_TEXT);
            if (isBlank(english) || !seen.add(english.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(VocabInsight.Example.builder()
                    .english(english)
                    .vietnamese(trim(example.getVietnamese(), MAX_TEXT))
                    .whyVi(trim(example.getWhyVi(), MAX_TEXT))
                    .setting(oneOf(example.getSetting(), SETTINGS))
                    .build());
            if (out.size() == limit) {
                break;
            }
        }
        return out;
    }

    private List<VocabInsight.SynonymComparison> comparisons(List<VocabInsight.SynonymComparison> raw) {
        Set<String> seenExamples = new LinkedHashSet<>();
        List<VocabInsight.SynonymComparison> out = new ArrayList<>();
        for (VocabInsight.SynonymComparison comparison : nullSafe(raw)) {
            if (comparison == null) {
                continue;
            }
            List<VocabInsight.ComparedWord> words = comparedWords(comparison.getWords());
            // A comparison of one word is not a comparison. Dropping it beats rendering
            // a table with a single row.
            if (words.size() < 2) {
                continue;
            }
            out.add(VocabInsight.SynonymComparison.builder()
                    .focus(trim(comparison.getFocus(), MAX_SHORT_TEXT))
                    .words(words)
                    .contrastExamples(examples(comparison.getContrastExamples(), MAX_CONTRAST_EXAMPLES, seenExamples))
                    .interchangeabilityVi(trim(comparison.getInterchangeabilityVi(), MAX_TEXT))
                    .build());
            if (out.size() == MAX_COMPARISONS) {
                break;
            }
        }
        return out;
    }

    private List<VocabInsight.ComparedWord> comparedWords(List<VocabInsight.ComparedWord> raw) {
        List<VocabInsight.ComparedWord> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (VocabInsight.ComparedWord word : nullSafe(raw)) {
            if (word == null) {
                continue;
            }
            String text = trim(word.getWord(), MAX_SHORT_TEXT);
            String coreIdeaVi = trim(word.getCoreIdeaVi(), MAX_TEXT);
            if (isBlank(text) || isBlank(coreIdeaVi) || !seen.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(VocabInsight.ComparedWord.builder()
                    .word(text)
                    .coreIdeaVi(coreIdeaVi)
                    .typicalContextVi(trim(word.getTypicalContextVi(), MAX_TEXT))
                    .nuanceVi(trim(word.getNuanceVi(), MAX_TEXT))
                    .registers(registers(word.getRegisters()))
                    .build());
            if (out.size() == MAX_COMPARED_WORDS) {
                break;
            }
        }
        return out;
    }

    private List<VocabInsight.Collocation> collocations(List<VocabInsight.Collocation> raw) {
        return mapped(raw, MAX_COLLOCATIONS, VocabInsight.Collocation::getPhrase, item ->
                VocabInsight.Collocation.builder()
                        .phrase(trim(item.getPhrase(), MAX_SHORT_TEXT))
                        .meaningVi(trim(item.getMeaningVi(), MAX_TEXT))
                        .example(trim(item.getExample(), MAX_TEXT))
                        .build());
    }

    private List<VocabInsight.GrammarPattern> grammarPatterns(List<VocabInsight.GrammarPattern> raw) {
        return mapped(raw, MAX_GRAMMAR_PATTERNS, VocabInsight.GrammarPattern::getPattern, item ->
                VocabInsight.GrammarPattern.builder()
                        .pattern(trim(item.getPattern(), MAX_SHORT_TEXT))
                        .explanationVi(trim(item.getExplanationVi(), MAX_TEXT))
                        .example(trim(item.getExample(), MAX_TEXT))
                        .build());
    }

    private List<VocabInsight.CommonMistake> mistakes(List<VocabInsight.CommonMistake> raw) {
        return mapped(raw, MAX_MISTAKES, VocabInsight.CommonMistake::getIncorrect, item ->
                VocabInsight.CommonMistake.builder()
                        .incorrect(trim(item.getIncorrect(), MAX_TEXT))
                        .corrected(trim(item.getCorrected(), MAX_TEXT))
                        .whyVi(trim(item.getWhyVi(), MAX_TEXT))
                        .type(oneOf(item.getType(), MISTAKE_TYPES))
                        .build());
    }

    private VocabInsight.IeltsGuidance guidance(VocabInsight.IeltsGuidance raw) {
        if (raw == null) {
            return null;
        }
        VocabInsight.IeltsGuidance guidance = VocabInsight.IeltsGuidance.builder()
                .speakingVi(trim(raw.getSpeakingVi(), MAX_TEXT))
                .writingVi(trim(raw.getWritingVi(), MAX_TEXT))
                .cautionVi(trim(raw.getCautionVi(), MAX_TEXT))
                .build();
        boolean empty = isBlank(guidance.getSpeakingVi())
                && isBlank(guidance.getWritingVi())
                && isBlank(guidance.getCautionVi());
        return empty ? null : guidance;
    }

    /**
     * Keeps only labels the product recognises. An invented label such as "business" would
     * render as a badge the learner cannot interpret, so it is dropped rather than shown.
     */
    private List<String> registers(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String label : nullSafe(raw)) {
            if (label == null) {
                continue;
            }
            String normalised = label.trim().toLowerCase(Locale.ROOT);
            if (VocabInsight.ALLOWED_REGISTERS.contains(normalised) && !out.contains(normalised)) {
                out.add(normalised);
            }
            if (out.size() == MAX_REGISTERS) {
                break;
            }
        }
        return out;
    }

    /** Drops entries whose identifying field is blank or repeated, then caps the list. */
    private <T> List<T> mapped(List<T> raw, int limit, Function<T, String> keyOf, UnaryOperator<T> copy) {
        List<T> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (T item : nullSafe(raw)) {
            if (item == null) {
                continue;
            }
            String key = keyOf.apply(item);
            if (isBlank(key) || !seen.add(key.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(copy.apply(item));
            if (out.size() == limit) {
                break;
            }
        }
        return out;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() <= max ? clean : clean.substring(0, max).trim() + "...";
    }

    private static String oneOf(String value, Set<String> allowed) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return allowed.contains(normalised) ? normalised : null;
    }
}
