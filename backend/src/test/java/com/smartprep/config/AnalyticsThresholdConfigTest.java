package com.smartprep.config;

import com.smartprep.model.enums.PerformanceLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The boundaries are the specification: accuracy below 60 is weak, 60 up to but not
 * including 80 is developing, 80 and above is strong. A one-point error in either direction
 * here would silently re-label every candidate's weaknesses.
 */
class AnalyticsThresholdConfigTest {

    private AnalyticsThresholdConfig config;

    @BeforeEach
    void setUp() {
        config = new AnalyticsThresholdConfig();
        ReflectionTestUtils.setField(config, "accuracyWeakBelow", 60.0);
        ReflectionTestUtils.setField(config, "accuracyStrongFrom", 80.0);
        ReflectionTestUtils.setField(config, "bandWeakBelow", new BigDecimal("5.5"));
        ReflectionTestUtils.setField(config, "bandStrongFrom", new BigDecimal("7.0"));
        ReflectionTestUtils.setField(config, "minSampleSize", 3);
    }

    @Test
    @DisplayName("accuracy: 59.9 is weak, 60 is developing, 79.9 is developing, 80 is strong")
    void accuracyBoundaries() {
        assertEquals(PerformanceLevel.WEAK, config.classifyAccuracy(0.0));
        assertEquals(PerformanceLevel.WEAK, config.classifyAccuracy(59.9));
        assertEquals(PerformanceLevel.DEVELOPING, config.classifyAccuracy(60.0));
        assertEquals(PerformanceLevel.DEVELOPING, config.classifyAccuracy(79.9));
        assertEquals(PerformanceLevel.STRONG, config.classifyAccuracy(80.0));
        assertEquals(PerformanceLevel.STRONG, config.classifyAccuracy(100.0));
    }

    @Test
    @DisplayName("band: 5.0 is weak, 5.5 is developing, 6.5 is developing, 7.0 is strong")
    void bandBoundaries() {
        assertEquals(PerformanceLevel.WEAK, config.classifyBand(new BigDecimal("5.0")));
        assertEquals(PerformanceLevel.DEVELOPING, config.classifyBand(new BigDecimal("5.5")));
        assertEquals(PerformanceLevel.DEVELOPING, config.classifyBand(new BigDecimal("6.5")));
        assertEquals(PerformanceLevel.STRONG, config.classifyBand(new BigDecimal("7.0")));
        assertEquals(PerformanceLevel.STRONG, config.classifyBand(new BigDecimal("9.0")));
    }

    @Test
    @DisplayName("the thresholds are read from configuration, not fixed in code")
    void thresholdsComeFromConfiguration() {
        ReflectionTestUtils.setField(config, "accuracyWeakBelow", 50.0);
        ReflectionTestUtils.setField(config, "accuracyStrongFrom", 90.0);

        assertEquals(PerformanceLevel.DEVELOPING, config.classifyAccuracy(55.0));
        assertEquals(PerformanceLevel.DEVELOPING, config.classifyAccuracy(85.0));
        assertEquals(PerformanceLevel.STRONG, config.classifyAccuracy(90.0));
        assertEquals(3, config.getMinSampleSize());
    }
}
