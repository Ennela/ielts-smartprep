package com.smartprep.dto.response;

import com.smartprep.model.enums.PerformanceLevel;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the result dashboard needs to answer, in order: how did I do, where am I weak,
 * which skill is pulling the score down, am I improving, and what should I practise next.
 *
 * <p>Sections are ordered the way the page reads them. {@code writing} is null until the
 * asynchronous essay grade has landed, and the summary says so rather than showing a zero.
 *
 * <p>The nested types carry no-args constructors because the question-level parts
 * ({@link ListeningAnalytics}, {@link ReadingAnalytics}, {@link WritingAnalytics}) are cached
 * in Redis as JSON and must deserialise again -- see {@code MockTestAnalyticsCalculator}.
 */
@Data
@Builder
public class MockTestAnalyticsResponse {

    private Long submissionId;
    private Long mockTestId;
    private String title;
    private SubmissionStatus status;
    private LocalDateTime submittedAt;

    private Summary summary;
    private List<SkillBreakdown> skills;
    private ReadingAnalytics reading;
    private ListeningAnalytics listening;
    private WritingAnalytics writing;
    private List<Weakness> weaknesses;
    private Progress progress;
    private List<Recommendation> recommendations;

    /** "How much did I get?" — the three graded bands and the honest scope of the overall. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private BigDecimal overallBand;
        private BigDecimal listeningBand;
        private BigDecimal readingBand;
        private BigDecimal writingBand;
        /** Always false: the platform has no Speaking module and does not pretend to. */
        private boolean speakingIncluded;
        private String speakingNote;
        private String overallNote;
        /**
         * Every graded skill sharing the lowest band. Two skills tied at the bottom are both
         * named rather than one picked arbitrarily; empty when all graded skills are equal.
         */
        private List<SkillType> weakestSkills;
    }

    /** One row per skill: band, raw accuracy where it exists, level and distance to target. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillBreakdown {
        private SkillType skill;
        private BigDecimal band;
        private Integer correct;
        private Integer total;
        private Double accuracy;
        private PerformanceLevel level;
        private BigDecimal targetBand;
        private BigDecimal gapToTarget;
        private boolean graded;
    }

    /** Accuracy over one slice of questions — a question type or a listening part. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupAccuracy {
        private String key;
        private String label;
        private int correct;
        private int total;
        private double accuracy;
        private PerformanceLevel level;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WrongQuestion {
        private Long questionId;
        private Integer orderIndex;
        private String section;
        private String questionType;
        private String questionText;
        private String userAnswer;
        private String correctAnswer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadingAnalytics {
        private int correct;
        private int total;
        private double accuracy;
        private PerformanceLevel level;
        private List<GroupAccuracy> byQuestionType;
        private List<WrongQuestion> wrongQuestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListeningAnalytics {
        private int correct;
        private int total;
        private double accuracy;
        private PerformanceLevel level;
        private List<GroupAccuracy> byPart;
        private List<GroupAccuracy> byQuestionType;
        private List<WrongQuestion> wrongQuestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WritingAnalytics {
        private BigDecimal band;
        private PerformanceLevel level;
        private WritingTask task1;
        private WritingTask task2;
        /** The four criteria combined across both tasks, Task 2 weighted double. */
        private List<CriterionScore> criteria;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WritingTask {
        private Long submissionId;
        private BigDecimal band;
        private Integer wordCount;
        private List<CriterionScore> criteria;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionScore {
        private String key;
        private String label;
        private BigDecimal band;
        private PerformanceLevel level;
    }

    /** One detected weak or developing slice, worst first. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Weakness {
        private SkillType skill;
        /** QUESTION_TYPE, PART or CRITERION. */
        private String category;
        private String key;
        private String label;
        private Double accuracy;
        private BigDecimal band;
        private Integer sampleSize;
        private PerformanceLevel level;
    }

    /** "Am I improving?" — every completed sitting, oldest first, and the change since the last. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Progress {
        private int attemptNumber;
        private int totalAttempts;
        private List<TimelinePoint> timeline;
        private List<SkillTrend> trends;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelinePoint {
        private Long submissionId;
        private int attemptNumber;
        private LocalDateTime submittedAt;
        private BigDecimal overallBand;
        private BigDecimal listeningBand;
        private BigDecimal readingBand;
        private BigDecimal writingBand;
        private boolean current;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillTrend {
        /** OVERALL, LISTENING, READING or WRITING. */
        private String skill;
        private BigDecimal current;
        private BigDecimal previous;
        private BigDecimal delta;
        /** IMPROVING, DECLINING, STABLE, FIRST_ATTEMPT or PENDING. */
        private String direction;
    }

    /** "What should I practise next?" — a concrete step with the route that starts it. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private SkillType skill;
        private String title;
        private String reason;
        private String actionPath;
    }
}
