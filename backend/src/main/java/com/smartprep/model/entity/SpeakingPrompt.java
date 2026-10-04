package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One Speaking task (V58). Part 1 and Part 3 hold a few questions, one per line;
 * Part 2 is a cue card: the task in questionText and the points in cuePoints.
 */
@Entity
@Table(name = "speaking_prompts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpeakingPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promptId;

    @Column(nullable = false)
    private Integer part;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String cuePoints;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
