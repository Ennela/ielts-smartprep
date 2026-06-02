package com.smartprep.model.entity;

import com.smartprep.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "readingQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();

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
    @Column(length = 255)
    private String groupLabel;

    /** Questions sharing the same group share this ID */
    private Integer groupId;

    /** Shared context for the group, e.g. summary text with blanks */
    @Column(columnDefinition = "TEXT")
    private String groupContext;
}
