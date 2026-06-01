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
    @Column(nullable = false, length = 30)
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

    // --- New fields for advanced question types ---

    /** JSON array of options for matching types, e.g. ["i. Heading A", "ii. Heading B", ...] */
    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    /** Word limit for completion types, e.g. 2 = "NO MORE THAN TWO WORDS" */
    private Integer wordLimit;

    /** Group label, e.g. "Questions 1-5: Matching Headings" */
    @Column(length = 100)
    private String groupLabel;

    /** Questions sharing the same group share this ID */
    private Integer groupId;

    /** Shared context for the group, e.g. summary text with blanks */
    @Column(columnDefinition = "TEXT")
    private String groupContext;
}
