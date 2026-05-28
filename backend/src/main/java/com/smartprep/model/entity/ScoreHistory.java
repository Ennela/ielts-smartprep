package com.smartprep.model.entity;

import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() { recordedAt = LocalDateTime.now(); }
}
