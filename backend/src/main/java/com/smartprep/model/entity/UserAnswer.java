package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_answers")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAnswer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long answerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "history_id", nullable = false)
    private ScoreHistory scoreHistory;

    @Column(nullable = false)
    private Integer questionNo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(nullable = false, length = 30)
    private String questionType;

    @Column(length = 500)
    private String userAnswer;

    @Column(nullable = false, length = 500)
    private String correctAnswer;

    @Column(nullable = false)
    private Boolean isCorrect;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(columnDefinition = "TEXT")
    private String evidenceText;

    private Integer evidenceOffset;

    private Integer evidenceLength;
}
