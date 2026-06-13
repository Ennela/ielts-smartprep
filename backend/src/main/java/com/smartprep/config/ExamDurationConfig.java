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
     * Returns the effective duration for an exam attempt.
     * Allows per-exam override (e.g. shorter practice tests).
     *
     * @param skill           the skill type
     * @param overrideSeconds optional override duration; null uses default
     * @return duration in seconds
     */
    public int getEffectiveDuration(SkillType skill, Integer overrideSeconds) {
        if (overrideSeconds != null && overrideSeconds > 0) {
            return overrideSeconds;
        }
        return getDefaultDuration(skill);
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
