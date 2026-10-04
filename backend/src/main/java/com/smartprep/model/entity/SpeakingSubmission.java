package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One recorded Speaking answer and its grade (V58). */
@Entity
@Table(name = "speaking_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpeakingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private SpeakingPrompt prompt;

    /** Object-storage key of the recording; served only to its owner. */
    @Column(nullable = false)
    private String audioKey;

    @Column(nullable = false, length = 50)
    private String audioMimeType;

    @Column(nullable = false)
    private Integer durationSeconds;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal overallBand;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal fluencyBand;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal lexicalBand;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal grammarBand;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal pronunciationBand;

    /** {"summary": "...", "strengths": [...], "improvements": [...], "criteria": {...}} */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String feedbackJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = LocalDateTime.now();
    }
}
