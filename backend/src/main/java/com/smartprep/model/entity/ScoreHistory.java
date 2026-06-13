package com.smartprep.model.entity;

import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "score_history")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoreHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SkillType skillType;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal score;

    @Column(length = 30)
    private String difficulty;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String moduleType = "ACADEMIC";

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    /** Total time spent in seconds (all skills) */
    private Integer timeSpentSeconds;

    /** Writing: time spent on Task 1 in seconds */
    private Integer timeSpentTask1;

    /** Writing: time spent on Task 2 in seconds */
    private Integer timeSpentTask2;

    /** True if auto-submitted when time expired */
    @Builder.Default
    private Boolean autoSubmitted = false;

    @OneToMany(mappedBy = "scoreHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("questionNo ASC")
    @Builder.Default
    private List<UserAnswer> userAnswers = new ArrayList<>();

    @PrePersist
    protected void onCreate() { recordedAt = LocalDateTime.now(); }
}
