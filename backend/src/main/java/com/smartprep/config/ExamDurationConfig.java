package com.smartprep.config;

import com.smartprep.model.enums.SkillType;
import org.springframework.stereotype.Component;

/**
 * Centralized exam duration configuration.
 * Replaces hard-coded timer values scattered across FE pages.
 * All durations are in seconds.
 */
@Component
public class ExamDurationConfig {

    // ── Default durations (IDP standard) ──
    public static final int READING_FULL_DURATION    = 3600;  // 60 min
    public static final int LISTENING_FULL_DURATION   = 1920;  // 32 min (audio + answer time)
    public static final int WRITING_FULL_DURATION     = 3600;  // 60 min

    // ── Writing per-task suggested durations (soft limits) ──
    public static final int WRITING_TASK1_SUGGESTED   = 1200;  // 20 min
    public static final int WRITING_TASK2_SUGGESTED   = 2400;  // 40 min

    // ── Grace period for late submissions ──
    public static final int DEADLINE_BUFFER_SECONDS   = 10;

    /**
     * How long after the deadline a submission is still accepted. Past it the server
     * refuses the answers. A minute, the same as a mock test section, because the page
     * submits on its own when the clock reaches zero and a browser can hold back the
     * timers of a tab in the background for about that long.
     */
    public static final int SUBMIT_GRACE_SECONDS      = 60;

    /**
     * Returns the effective duration for an exam attempt.
     * Allows per-exam override (e.g. shorter practice tests).
     *
     * @param skill           the skill type
     * @param overrideSeconds optional override duration; null uses default
     * @return duration in seconds
     */
    public int getEffectiveDuration(SkillType skill, Integer overrideSeconds) {
        int defaultDuration = getDefaultDuration(skill);
        if (overrideSeconds != null && overrideSeconds > 0) {
            // The override comes straight from the client, so it may only ever shorten an
            // exam. Without this clamp a request could ask for an arbitrarily long deadline
            // and the "server-authoritative timer" below would grant it.
            return Math.min(overrideSeconds, defaultDuration);
        }
        return defaultDuration;
    }

    /**
     * Returns the default IDP-standard duration for the given skill.
     */
    public int getDefaultDuration(SkillType skill) {
        return switch (skill) {
            case READING   -> READING_FULL_DURATION;
            case LISTENING -> LISTENING_FULL_DURATION;
            case WRITING   -> WRITING_FULL_DURATION;
            default        -> 3600;
        };
    }
}
