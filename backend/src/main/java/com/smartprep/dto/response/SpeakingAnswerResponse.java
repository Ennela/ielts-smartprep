package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;

/** The answer to one Part 1/3 question, with what the examiner said about it. */
@Data
@Builder
public class SpeakingAnswerResponse {
    /** Position of the question in the prompt, from 0; also the recording's path segment. */
    private int questionIndex;
    private String question;
    private int durationSeconds;
    private String transcript;
    private String comment;
}
