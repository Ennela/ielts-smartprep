package com.smartprep.model.enums;

public enum EssayType {
    // Task 2 types
    OPINION,
    DISCUSSION,
    CAUSE_AND_EFFECT,
    PROBLEM_AND_SOLUTION,
    ADVANTAGES_DISADVANTAGES,
    TWO_PART_QUESTION,

    // Task 1 types
    LINE_GRAPH,
    BAR_CHART,
    PIE_CHART,
    TABLE,
    MAP,
    DIAGRAM;

    /**
     * Returns true if this essay type belongs to IELTS Writing Task 1.
     */
    public boolean isTask1() {
        return this == LINE_GRAPH
            || this == BAR_CHART
            || this == PIE_CHART
            || this == TABLE
            || this == MAP
            || this == DIAGRAM;
    }
}
