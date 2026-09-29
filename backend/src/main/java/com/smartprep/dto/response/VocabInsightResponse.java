package com.smartprep.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The explanation for one saved word, plus why it is or is not there.
 *
 * <p>A failed generation is a 200 with {@code status = UNAVAILABLE} rather than an error
 * status: the learner is on a page that still has the word, its meaning and its review
 * schedule, and losing all of that because the AI timed out would be the wrong trade.
 * A word nobody has asked about yet is {@code NOT_GENERATED}, which is not a failure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VocabInsightResponse {

    public enum Status {
        /** An explanation is present in {@link #insight}. */
        READY,
        /** Nothing has been generated for this word yet. Ask for one with a POST. */
        NOT_GENERATED,
        /** Generation failed or the model returned nothing usable; {@link #message} says so. */
        UNAVAILABLE
    }

    private Long vocabId;
    private String word;
    private Status status;

    /** When this explanation was generated. Null when none exists. */
    private LocalDateTime generatedAt;

    private VocabInsight insight;

    /** Learner-facing reason, set when the status is UNAVAILABLE. */
    private String message;
}
