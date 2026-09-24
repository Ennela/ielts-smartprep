package com.smartprep.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The context-aware explanation of one vocabulary item.
 *
 * <p>This is the shape the AI is asked to produce, the shape stored in
 * {@code vocabulary.insight_json}, and the shape the vocabulary page renders. Every
 * section below the core meaning is optional: a word with no useful synonym distinction
 * simply has no comparison, and the page renders without that section rather than
 * inventing one.
 *
 * <p>Unknown fields are ignored so that a future prompt revision that adds a key cannot
 * break the explanations already stored.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VocabInsight {

    /** Register labels the product recognises. Anything else the AI returns is discarded. */
    public static final List<String> ALLOWED_REGISTERS =
            List.of("informal", "neutral", "formal", "academic", "slang", "literary");

    private String word;
    private String partOfSpeech;

    /** IPA, e.g. {@code /trʌst/}. Omitted when the model has no reliable pronunciation. */
    private String ipa;

    private String coreMeaningVi;
    private String definitionEn;

    /** One or more of {@link #ALLOWED_REGISTERS}; a word whose register shifts carries several. */
    private List<String> registers;

    /** Why the register is what it is, or why it depends on the context. */
    private String registerNoteVi;

    /** One or two sentences on what a speaker actually implies by choosing this word. */
    private String contextSummaryVi;

    private List<Sense> senses;
    private List<SynonymComparison> synonymComparisons;
    private List<Collocation> collocations;
    private List<GrammarPattern> grammarPatterns;
    private List<CommonMistake> commonMistakes;
    private IeltsGuidance ieltsGuidance;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        private String meaningVi;
        private String definitionEn;
        /** The situation that gives rise to this sense, and the nuance it carries. */
        private String contextVi;
        private List<String> registers;
        private List<Example> examples;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Example {
        private String english;
        private String vietnamese;
        /** Why this word fits this sentence, including nuance a literal translation loses. */
        private String whyVi;
        /** EVERYDAY, IELTS_SPEAKING or IELTS_WRITING. */
        private String setting;
    }

    /**
     * One comparison against near-synonyms — the section that answers
     * "why this word and not that one".
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SynonymComparison {
        /** What the comparison is about, e.g. "belief vs faith vs trust". */
        private String focus;
        private List<ComparedWord> words;
        private List<Example> contrastExamples;
        /** When the words may be swapped, and what changes when they are. */
        private String interchangeabilityVi;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComparedWord {
        private String word;
        private String coreIdeaVi;
        private String typicalContextVi;
        private String nuanceVi;
        private List<String> registers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Collocation {
        private String phrase;
        private String meaningVi;
        private String example;
    }

    /**
     * A pattern the word appears in — {@code a chance to do sth}, countability,
     * transitivity, the preposition that follows it.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GrammarPattern {
        private String pattern;
        private String explanationVi;
        private String example;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommonMistake {
        private String incorrect;
        private String corrected;
        private String whyVi;
        /**
         * GRAMMAR (ungrammatical), UNNATURAL (grammatical but no one says it),
         * REGISTER (right words, wrong situation) or CONTEXT (right word, wrong sense).
         * The distinction matters: a learner should know whether a sentence is wrong
         * or merely odd.
         */
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IeltsGuidance {
        private String speakingVi;
        private String writingVi;
        /** When the word would sound memorised, forced, or wrong for the task. */
        private String cautionVi;
    }
}
