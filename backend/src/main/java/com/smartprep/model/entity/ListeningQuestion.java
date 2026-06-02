package com.smartprep.model.entity;

import com.smartprep.model.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    @Column(nullable = false, length = 15)
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
}
