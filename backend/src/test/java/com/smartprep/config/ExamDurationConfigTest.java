package com.smartprep.config;

import com.smartprep.model.enums.SkillType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the rule that a client-supplied duration override may only ever shorten an exam.
 * <p>
 * {@code StartAttemptRequest.durationOverride} previously flowed through unbounded, so a
 * request could ask for an arbitrarily long deadline and the "server-authoritative timer"
 * would grant it — letting a user take a timed exam with effectively unlimited time.
 */
class ExamDurationConfigTest {

    private final ExamDurationConfig config = new ExamDurationConfig();

    @Test
    @DisplayName("falls back to the standard duration when no override is given")
    void noOverride_usesDefault() {
        assertEquals(ExamDurationConfig.READING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.READING, null));
        assertEquals(ExamDurationConfig.LISTENING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.LISTENING, null));
        assertEquals(ExamDurationConfig.WRITING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.WRITING, null));
    }

    @Test
    @DisplayName("honours an override that shortens the exam")
    void shorterOverride_isHonoured() {
        assertEquals(600, config.getEffectiveDuration(SkillType.READING, 600));
    }

    @Test
    @DisplayName("clamps an override that would extend the exam beyond the standard duration")
    void longerOverride_isClampedToDefault() {
        assertEquals(ExamDurationConfig.READING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.READING, 999_999_999));
        // Listening is the shortest exam, so it is the easiest to overshoot accidentally.
        assertEquals(ExamDurationConfig.LISTENING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.LISTENING,
                        ExamDurationConfig.LISTENING_FULL_DURATION + 1));
    }

    @Test
    @DisplayName("treats zero and negative overrides as absent")
    void nonPositiveOverride_usesDefault() {
        assertEquals(ExamDurationConfig.WRITING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.WRITING, 0));
        assertEquals(ExamDurationConfig.WRITING_FULL_DURATION,
                config.getEffectiveDuration(SkillType.WRITING, -1));
    }
}
