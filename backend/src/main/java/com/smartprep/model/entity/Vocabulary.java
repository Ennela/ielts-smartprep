package com.smartprep.model.entity;

import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vocabulary")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vocab_id")
    private Long vocabId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(length = 100)
    private String phonetic;

    @Column(name = "part_of_speech", length = 50)
    private String partOfSpeech;

    @Column(name = "meaning_vi", nullable = false, columnDefinition = "TEXT")
    private String meaningVi;

    @Column(columnDefinition = "TEXT")
    private String example;

    @Column(columnDefinition = "TEXT")
    private String collocation;

    @Column(name = "ease_factor", nullable = false)
    @Builder.Default
    private Double easeFactor = 2.5;

    @Column(name = "interval_days", nullable = false)
    @Builder.Default
    private Integer intervalDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer repetitions = 0;

    @Column(name = "due_date", nullable = false)
    @Builder.Default
    private LocalDateTime dueDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source_skill", length = 50)
    private SkillType sourceSkill;

    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    @Column(name = "cefr_level", length = 10)
    private String cefrLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dueDate == null) {
            dueDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
