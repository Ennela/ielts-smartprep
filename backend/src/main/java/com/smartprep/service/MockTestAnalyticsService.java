package com.smartprep.service;

import com.smartprep.config.AnalyticsThresholdConfig;
import com.smartprep.dto.response.MockTestAnalyticsResponse;
import com.smartprep.dto.response.MockTestAnalyticsResponse.*;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.PerformanceLevel;
import com.smartprep.model.enums.SkillType;
import com.smartprep.model.enums.SubmissionStatus;
import com.smartprep.repository.MockTestSubmissionRepository;
import com.smartprep.service.MockTestAnalyticsCalculator.QuestionAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns one mock test submission into the analytics the result dashboard shows.
 *
 * <p>Nothing here is stored. The submission already holds the bands, the session holds the
 * answers, and the mock test holds the questions; every figure below is derived from those
 * on read, the same way {@code MockTestService.getSubmission} rebuilds the reading review.
 * That keeps the feature free of new tables and of a second copy of the score that could
 * drift from the first.
 *
 * <p>The question-level part -- accuracy per type, per part, per writing criterion -- is
 * the expensive part and comes from {@link MockTestAnalyticsCalculator}, which caches it.
 * What is assembled here on top of it (targets, the attempt timeline, the recommendations)
 * depends on state that changes between requests and is cheap, so it is always fresh.
 *
 * <p>Weakness detection is deliberately not an AI decision. It is arithmetic over the
 * answers plus the thresholds in {@link AnalyticsThresholdConfig}, so the same test always
 * produces the same diagnosis and a candidate can be told exactly why.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MockTestAnalyticsService {

    private final MockTestSubmissionRepository submissionRepository;
    private final MockTestAnalyticsCalculator calculator;
    private final AnalyticsThresholdConfig thresholds;

    /** Falls back to this when the profile has no target for a skill; matches StatsService. */
    private static final BigDecimal DEFAULT_TARGET_BAND = new BigDecimal("6.5");
    private static final int MAX_RECOMMENDATIONS = 3;

    private static final Map<SkillType, String> PRACTICE_PATHS = Map.of(
            SkillType.LISTENING, "/listening",
            SkillType.READING, "/reading",
            SkillType.WRITING, "/writing");

    @Transactional(readOnly = true)
    public MockTestAnalyticsResponse getAnalytics(Long userId, Long submissionId) {
        MockTestSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        if (!sub.getUser().getUserId().equals(userId)) {
            // Same exception and message as a genuine miss: a caller must not be able to tell
            // "exists but is not yours" from "does not exist". Matches MockTestService.
            throw new ResourceNotFoundException("Submission not found with id: " + submissionId);
        }

        MockTest mockTest = sub.getMockTest();
        boolean writingGraded = sub.getStatus() == SubmissionStatus.COMPLETED;

        QuestionAnalytics questions = calculator.compute(sub.getSubmissionId(), sub.getStatus());
        ListeningAnalytics listening = questions.getListening();
        ReadingAnalytics reading = questions.getReading();
        WritingAnalytics writing = questions.getWriting();

        List<SkillBreakdown> skills = buildSkillBreakdown(sub, sub.getUser(), listening, reading, writing);
        List<Weakness> weaknesses = detectWeaknesses(listening, reading, writing);

        // Completed sittings only, oldest first: a FAILED or still-grading submission has no
        // writing band, so plotting it would put a false dip in every timeline.
        List<MockTestSubmission> completed = submissionRepository
                .findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .filter(s -> s.getStatus() == SubmissionStatus.COMPLETED)
                .sorted(Comparator.comparing(MockTestSubmission::getSubmittedAt))
                .collect(Collectors.toList());
        Progress progress = buildProgress(sub, completed);

        return MockTestAnalyticsResponse.builder()
                .submissionId(sub.getSubmissionId())
                .mockTestId(mockTest.getMockTestId())
                .title(mockTest.getTitle())
                .status(sub.getStatus())
                .submittedAt(sub.getSubmittedAt())
                .summary(buildSummary(sub, writingGraded, skills))
                .skills(skills)
                .reading(reading)
                .listening(listening)
                .writing(writing)
                .weaknesses(weaknesses)
                .progress(progress)
                .recommendations(buildRecommendations(weaknesses, skills))
                .build();
    }

    // ========== Skill breakdown & summary ==========

    private List<SkillBreakdown> buildSkillBreakdown(MockTestSubmission sub, User user,
                                                     ListeningAnalytics listening,
                                                     ReadingAnalytics reading,
                                                     WritingAnalytics writing) {
        List<SkillBreakdown> skills = new ArrayList<>();
        skills.add(skillRow(SkillType.LISTENING, sub.getListeningScore(),
                listening.getCorrect(), listening.getTotal(), listening.getAccuracy(), listening.getLevel(),
                user.getTargetListeningScore(), true));
        skills.add(skillRow(SkillType.READING, sub.getReadingScore(),
                reading.getCorrect(), reading.getTotal(), reading.getAccuracy(), reading.getLevel(),
                user.getTargetReadingScore(), true));
        if (writing != null) {
            skills.add(skillRow(SkillType.WRITING, writing.getBand(),
                    null, null, null, writing.getLevel(),
                    user.getTargetWritingScore(), true));
        } else {
            skills.add(skillRow(SkillType.WRITING, null, null, null, null, null,
                    user.getTargetWritingScore(), false));
        }
        return skills;
    }

    private SkillBreakdown skillRow(SkillType skill, BigDecimal band, Integer correct, Integer total,
                                    Double accuracy, PerformanceLevel level, BigDecimal target,
                                    boolean graded) {
        BigDecimal safeTarget = target != null ? target : DEFAULT_TARGET_BAND;
        return SkillBreakdown.builder()
                .skill(skill)
                .band(band)
                .correct(correct)
                .total(total)
                .accuracy(accuracy)
                .level(level)
                .targetBand(safeTarget)
                .gapToTarget(band == null ? null : safeTarget.subtract(band).max(BigDecimal.ZERO))
                .graded(graded)
                .build();
    }

    private Summary buildSummary(MockTestSubmission sub, boolean writingGraded, List<SkillBreakdown> skills) {
        return Summary.builder()
                .overallBand(writingGraded ? sub.getOverallBand() : null)
                .listeningBand(sub.getListeningScore())
                .readingBand(sub.getReadingScore())
                .writingBand(writingGraded ? sub.getWritingScore() : null)
                .speakingIncluded(false)
                .speakingNote("Speaking: Not included")
                .overallNote(writingGraded
                        ? "Average of Listening, Reading and Writing. This is a 3-skill band, not a full IELTS 4-skill result."
                        : "Overall band is available once the Writing evaluation has finished.")
                .weakestSkills(weakestSkills(skills))
                .build();
    }

    /**
     * "Which skill is pulling my score down?" — every graded skill at the lowest band,
     * provided something is actually above it. Three equal bands name no weakest skill.
     */
    private List<SkillType> weakestSkills(List<SkillBreakdown> skills) {
        List<SkillBreakdown> graded = skills.stream()
                .filter(s -> s.isGraded() && s.getBand() != null)
                .collect(Collectors.toList());
        if (graded.size() < 2) {
            return List.of();
        }
        BigDecimal lowest = graded.stream().map(SkillBreakdown::getBand).min(Comparator.naturalOrder()).get();
        List<SkillType> atLowest = graded.stream()
                .filter(s -> s.getBand().compareTo(lowest) == 0)
                .map(SkillBreakdown::getSkill)
                .collect(Collectors.toList());
        return atLowest.size() == graded.size() ? List.of() : atLowest;
    }

    // ========== Weakness detection ==========

    private List<Weakness> detectWeaknesses(ListeningAnalytics listening, ReadingAnalytics reading,
                                            WritingAnalytics writing) {
        List<Weakness> found = new ArrayList<>();
        int minSample = thresholds.getMinSampleSize();

        for (GroupAccuracy g : listening.getByPart()) {
            addIfWeak(found, SkillType.LISTENING, "PART", g, minSample);
        }
        for (GroupAccuracy g : listening.getByQuestionType()) {
            addIfWeak(found, SkillType.LISTENING, "QUESTION_TYPE", g, minSample);
        }
        for (GroupAccuracy g : reading.getByQuestionType()) {
            addIfWeak(found, SkillType.READING, "QUESTION_TYPE", g, minSample);
        }
        if (writing != null) {
            for (CriterionScore c : writing.getCriteria()) {
                if (c.getLevel() != null && c.getLevel() != PerformanceLevel.STRONG) {
                    found.add(Weakness.builder()
                            .skill(SkillType.WRITING)
                            .category("CRITERION")
                            .key(c.getKey())
                            .label(c.getLabel())
                            .band(c.getBand())
                            .level(c.getLevel())
                            .build());
                }
            }
        }

        // WEAK before DEVELOPING; within a level, lowest accuracy or band first. A writing
        // criterion is placed by its band as a share of 9 so it can be ordered against an
        // accuracy without inventing a second list.
        found.sort(Comparator.comparing(Weakness::getLevel)
                .thenComparingDouble(w -> w.getAccuracy() != null
                        ? w.getAccuracy()
                        : w.getBand().doubleValue() / 9.0 * 100.0));
        return found;
    }

    private void addIfWeak(List<Weakness> found, SkillType skill, String category,
                           GroupAccuracy g, int minSample) {
        if (g.getLevel() == PerformanceLevel.STRONG || g.getTotal() < minSample) {
            return;
        }
        found.add(Weakness.builder()
                .skill(skill)
                .category(category)
                .key(g.getKey())
                .label(g.getLabel())
                .accuracy(g.getAccuracy())
                .sampleSize(g.getTotal())
                .level(g.getLevel())
                .build());
    }

    // ========== Progress ==========

    private Progress buildProgress(MockTestSubmission current, List<MockTestSubmission> completed) {
        List<TimelinePoint> timeline = new ArrayList<>();
        int attemptNumber = 0;
        for (int i = 0; i < completed.size(); i++) {
            MockTestSubmission s = completed.get(i);
            boolean isCurrent = s.getSubmissionId().equals(current.getSubmissionId());
            if (isCurrent) {
                attemptNumber = i + 1;
            }
            timeline.add(TimelinePoint.builder()
                    .submissionId(s.getSubmissionId())
                    .attemptNumber(i + 1)
                    .submittedAt(s.getSubmittedAt())
                    .overallBand(s.getOverallBand())
                    .listeningBand(s.getListeningScore())
                    .readingBand(s.getReadingScore())
                    .writingBand(s.getWritingScore())
                    .current(isCurrent)
                    .build());
        }

        // The previous completed sitting is the comparison point. For a submission still
        // grading, that is the most recent completed one; for a completed one, the one before.
        MockTestSubmission previous = null;
        if (attemptNumber > 1) {
            previous = completed.get(attemptNumber - 2);
        } else if (attemptNumber == 0 && !completed.isEmpty()) {
            previous = completed.get(completed.size() - 1);
        }
        boolean currentCompleted = current.getStatus() == SubmissionStatus.COMPLETED;

        List<SkillTrend> trends = List.of(
                trend("OVERALL", currentCompleted ? current.getOverallBand() : null,
                        previous == null ? null : previous.getOverallBand(), currentCompleted),
                trend("LISTENING", current.getListeningScore(),
                        previous == null ? null : previous.getListeningScore(), true),
                trend("READING", current.getReadingScore(),
                        previous == null ? null : previous.getReadingScore(), true),
                trend("WRITING", currentCompleted ? current.getWritingScore() : null,
                        previous == null ? null : previous.getWritingScore(), currentCompleted));

        return Progress.builder()
                .attemptNumber(attemptNumber)
                .totalAttempts(completed.size())
                .timeline(timeline)
                .trends(trends)
                .build();
    }

    private SkillTrend trend(String skill, BigDecimal current, BigDecimal previous, boolean graded) {
        SkillTrend.SkillTrendBuilder b = SkillTrend.builder().skill(skill).current(current).previous(previous);
        if (!graded || current == null) {
            return b.direction("PENDING").build();
        }
        if (previous == null) {
            return b.direction("FIRST_ATTEMPT").build();
        }
        BigDecimal delta = current.subtract(previous);
        String direction = delta.signum() > 0 ? "IMPROVING" : delta.signum() < 0 ? "DECLINING" : "STABLE";
        return b.delta(delta).direction(direction).build();
    }

    // ========== Recommendations ==========

    /**
     * "What should I practise next?" — up to three concrete steps, worst weakness first,
     * never two for the same skill so a candidate weak across the board sees breadth.
     */
    private List<Recommendation> buildRecommendations(List<Weakness> weaknesses, List<SkillBreakdown> skills) {
        List<Recommendation> recs = new ArrayList<>();
        Set<SkillType> covered = EnumSet.noneOf(SkillType.class);

        for (Weakness w : weaknesses) {
            if (recs.size() >= MAX_RECOMMENDATIONS || covered.contains(w.getSkill())) {
                continue;
            }
            covered.add(w.getSkill());
            recs.add(Recommendation.builder()
                    .skill(w.getSkill())
                    .title(recommendationTitle(w))
                    .reason(recommendationReason(w))
                    .actionPath(PRACTICE_PATHS.get(w.getSkill()))
                    .build());
        }

        if (recs.isEmpty()) {
            boolean pending = skills.stream().anyMatch(s -> !s.isGraded());
            recs.add(Recommendation.builder()
                    .title(pending ? "Wait for the Writing evaluation" : "Take another full mock test")
                    .reason(pending
                            ? "Listening and Reading show no weak areas so far. Writing recommendations will appear once grading finishes."
                            : "No weak areas were detected in this sitting. A second full test will show whether the result is consistent.")
                    .actionPath("/mock-tests")
                    .build());
        }
        return recs;
    }

    private String recommendationTitle(Weakness w) {
        String skill = w.getSkill().name().charAt(0) + w.getSkill().name().substring(1).toLowerCase();
        return switch (w.getCategory()) {
            case "PART" -> "Practise Listening " + w.getLabel();
            case "CRITERION" -> "Work on Writing: " + w.getLabel();
            default -> "Practise " + skill + ": " + w.getLabel();
        };
    }

    private String recommendationReason(Weakness w) {
        if (w.getAccuracy() != null) {
            return String.format("%s accuracy in this test (%d questions).",
                    formatPercent(w.getAccuracy()), w.getSampleSize());
        }
        return String.format("Scored band %s across both tasks, below the %s mark.",
                w.getBand().toPlainString(),
                w.getLevel() == PerformanceLevel.WEAK ? "developing" : "strong");
    }

    private static String formatPercent(double value) {
        return (value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value)) + "%";
    }
}
