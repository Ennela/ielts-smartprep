package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_question_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ReadingQuestion readingQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listening_question_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ListeningQuestion listeningQuestion;

    @Column(nullable = false, length = 10)
    private String label;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private Boolean isCorrect;

    @Column(nullable = false)
    private Integer orderIndex;
}
