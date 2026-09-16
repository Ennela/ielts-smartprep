package com.smartprep.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the merged history feed: a reading quiz, a listening test, a writing essay
 * or a full mock test, in one shape so the History page can list them together.
 *
 * <p>{@code refId} is the id in the skill's own table -- the quiz, test, submission or
 * mock test submission -- and is what the review link for that skill needs. {@code title}
 * is the skill's own label (reading topic, listening test mode, writing essay type, mock
 * test title); the client formats it. {@code status} is set for mock tests only, so a
 * sitting still being graded can say so instead of showing a band.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryFeedItemResponse {

    /** READING, LISTENING, WRITING or MOCK_TEST */
    private String skill;
    private Long refId;
    private String title;
    private BigDecimal score;
    private String status;
    private LocalDateTime submittedAt;
}
