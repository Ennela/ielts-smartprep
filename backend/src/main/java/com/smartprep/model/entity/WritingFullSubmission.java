package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "writing_full_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WritingFullSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task1_submission_id", nullable = false)
    private WritingSubmission task1Submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task2_submission_id", nullable = false)
    private WritingSubmission task2Submission;

    @Column(name = "overall_band", precision = 2, scale = 1, nullable = false)
    private BigDecimal overallBand;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}
