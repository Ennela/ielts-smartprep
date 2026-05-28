package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "writing_submissions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WritingSubmission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_id", nullable = false)
    private WritingPrompt prompt;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String essayText;

    private Integer wordCount;

    @Column(precision = 2, scale = 1)
    private BigDecimal overallBand;

    @Column(precision = 2, scale = 1)
    private BigDecimal taskResponseScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal coherenceScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal lexicalScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal grammarScore;

    @Column(columnDefinition = "JSON")
    private String errorListJson;

    @Column(columnDefinition = "TEXT")
    private String rewrittenVersion;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() { submittedAt = LocalDateTime.now(); }
}
