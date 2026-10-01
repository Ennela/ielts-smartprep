package com.smartprep.model.entity;

import com.smartprep.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "listening_questions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ListeningQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private ListeningPart part;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    // 30, not 15: MATCHING_SENTENCE_ENDINGS is 25 characters. See V45.
    @Column(nullable = false, length = 30)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @OneToMany(mappedBy = "listeningQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();

    @Column(nullable = false)
    private String correctAnswer;

    @Column(nullable = false)
    private Integer orderIndex;

    // --- Grouping, as on ReadingQuestion (V54) ---

    /** Questions sharing the same group share this ID */
    private Integer groupId;

    /** Group label, e.g. "Questions 16-20: Choose FIVE answers from the box" */
    @Column(length = 255)
    private String groupLabel;

    /** Shared context for the group: the form or notes, with gaps written ___N___ */
    @Column(columnDefinition = "TEXT")
    private String groupContext;

    /** JSON array of options the group chooses from, e.g. ["A. car park", "B. cafe", ...] */
    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    /** Word limit for completion types, e.g. 2 = "NO MORE THAN TWO WORDS" */
    private Integer wordLimit;

    /**
     * How many letters a multiple-choice task takes: 1, or 2 for "Choose TWO letters" (V55).
     * Every row of such a task carries the same count and its own correct letter.
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer selectCount = 1;

    /** The diagram, map or plan the group labels (V56); a path on this site. */
    @Column(length = 512)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Builder.Default
    @Column(nullable = false)
    private Boolean verified = false;
}
