package com.smartprep.model.entity;

import com.smartprep.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reading_questions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReadingQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private ReadingQuiz quiz;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    @Column(nullable = false)
    private String correctAnswer;

    private String userAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private Integer orderIndex;
}
