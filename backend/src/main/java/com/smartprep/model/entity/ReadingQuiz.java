package com.smartprep.model.entity;

import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reading_quizzes")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReadingQuiz {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String passageText;

    private Integer timeLimitSeconds;

    @Column(precision = 2, scale = 1)
    private BigDecimal score;

    @Builder.Default
    private Integer totalQuestions = 5;

    private Integer correctAnswers;

    private LocalDateTime submittedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReadingQuestion> questions = new ArrayList<>();

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
