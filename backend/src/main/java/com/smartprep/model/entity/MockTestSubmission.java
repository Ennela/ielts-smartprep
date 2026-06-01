package com.smartprep.model.entity;

import com.smartprep.model.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mock_test_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockTestSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long submissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_test_id", nullable = false)
    private MockTest mockTest;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(precision = 2, scale = 1)
    private BigDecimal listeningScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal readingScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal writingScore;

    @Column(precision = 2, scale = 1)
    private BigDecimal overallBand;

    private Integer listeningCorrectAnswers;
    private Integer readingCorrectAnswers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_task1_submission_id")
    private WritingSubmission writingTask1Submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_task2_submission_id")
    private WritingSubmission writingTask2Submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listening_test_id")
    private ListeningTest listeningTest;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}
