package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** A Speaking task with the timing the client enforces while recording. */
@Data
@Builder
public class SpeakingPromptResponse {
    private Long promptId;
    private int part;
    private String topic;
    /** Part 1 and 3: the questions to answer. Part 2: one line, the task. */
    private List<String> questions;
    /** Part 2 only: the "You should say" points. */
    private List<String> cuePoints;
    /** Thinking time before recording starts (Part 2: 60 s, else 0). */
    private int prepSeconds;
    /** Longest recording accepted: the Part 2 answer, or each Part 1/3 answer. */
    private int maxSpeakSeconds;
}
