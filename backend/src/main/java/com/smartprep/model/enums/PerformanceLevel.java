package com.smartprep.model.enums;

/**
 * How a candidate performed on one slice of a mock test: a question type, a listening
 * part, a writing criterion or a whole skill.
 *
 * <p>Assigned deterministically from accuracy or band by
 * {@link com.smartprep.config.AnalyticsThresholdConfig} — never by the AI.
 */
public enum PerformanceLevel {
    WEAK,
    DEVELOPING,
    STRONG
}
