package com.smartprep.model.entity;

import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Server-authoritative exam attempt record.
 * Tracks start time, deadline, and actual time spent for anti-cheat timer enforcement.
 */
@Entity
@Table(name = "exam_attempts",
        indexes = @Index(name = "idx_attempt_user_skill_status",
                columnList = "user_id, skill_type, status"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SkillType skillType;

    /** Total allowed duration in seconds */
    @Column(nullable = false)
    private Integer durationSeconds;

    /** Server timestamp when the attempt started */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Hard deadline = startedAt + durationSeconds */
    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    /** True if the system auto-submitted when time ran out */
    @Builder.Default
    private Boolean autoSubmitted = false;

    /** Actual total time spent (set on completion) */
    private Integer timeSpentSeconds;

    /** Writing: actual seconds spent on Task 1 */
    private Integer timeSpentTask1;

    /** Writing: actual seconds spent on Task 2 */
    private Integer timeSpentTask2;

    /**
     * JSON reference IDs for the exam content.
     * Reading: "[quizId1, quizId2, quizId3]"
     * Listening: "[partId1, partId2, ...]"
     * Writing: "[promptId1, promptId2]"
     */
    @Column(columnDefinition = "TEXT")
    private String examReferenceIds;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
