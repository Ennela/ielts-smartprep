package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

/** The recorded answer to one question of a Part 1 or Part 3 submission (V59). */
@Entity
@Table(name = "speaking_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpeakingAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long answerId;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private SpeakingSubmission submission;

    /** Position of the question in the prompt, from 0. */
    @Column(nullable = false)
    private Integer questionIndex;

    /** Object-storage key of the recording; served only to its owner. */
    @Column(nullable = false)
    private String audioKey;

    @Column(nullable = false, length = 50)
    private String audioMimeType;

    @Column(nullable = false)
    private Integer durationSeconds;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
