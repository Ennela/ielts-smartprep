package com.smartprep.config;

import com.smartprep.model.enums.PerformanceLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The one place that decides what counts as weak, developing or strong.
 *
 * <p>Weakness detection is deterministic on purpose: a candidate who got 4 of 10 matching
 * questions right is weak at matching whether or not Gemini agrees, and a rule that lives in
 * configuration can be explained, tested and tuned without a prompt change. The numbers are
 * read from {@code app.analytics.*} so they are not repeated across services.
 *
 * <p>Two scales are needed because the skills are not measured the same way. Listening and
 * Reading produce a correct/total count, so they classify on accuracy percent. Writing is
 * graded on four band-scored criteria with no notion of "correct", so its slices classify on
 * the band itself.
 */
@Component
public class AnalyticsThresholdConfig {

    /** Accuracy strictly below this percentage is WEAK. */
    @Value("${app.analytics.accuracy-weak-below:60}")
    private double accuracyWeakBelow;

    /** Accuracy at or above this percentage is STRONG; between the two is DEVELOPING. */
    @Value("${app.analytics.accuracy-strong-from:80}")
    private double accuracyStrongFrom;

    /** A band strictly below this is WEAK. */
    @Value("${app.analytics.band-weak-below:5.5}")
    private BigDecimal bandWeakBelow;

    /** A band at or above this is STRONG; between the two is DEVELOPING. */
    @Value("${app.analytics.band-strong-from:7.0}")
    private BigDecimal bandStrongFrom;

    /**
     * A question-type or part group needs at least this many questions before it is reported
     * as a weakness. Every group still appears in the breakdown with its level; this only
     * gates the "you are weak at X" list and the recommendations built from it, where one
     * missed question out of one would otherwise read as 0% and top the list.
     */
    @Value("${app.analytics.min-sample-size:3}")
    private int minSampleSize;

    public PerformanceLevel classifyAccuracy(double accuracyPercent) {
        if (accuracyPercent < accuracyWeakBelow) {
            return PerformanceLevel.WEAK;
        }
        if (accuracyPercent >= accuracyStrongFrom) {
            return PerformanceLevel.STRONG;
        }
        return PerformanceLevel.DEVELOPING;
    }

    public PerformanceLevel classifyBand(BigDecimal band) {
        if (band.compareTo(bandWeakBelow) < 0) {
            return PerformanceLevel.WEAK;
        }
        if (band.compareTo(bandStrongFrom) >= 0) {
            return PerformanceLevel.STRONG;
        }
        return PerformanceLevel.DEVELOPING;
    }

    public int getMinSampleSize() {
        return minSampleSize;
    }
}
