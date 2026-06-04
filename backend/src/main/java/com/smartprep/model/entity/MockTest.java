package com.smartprep.model.entity;

import com.smartprep.model.enums.Difficulty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mock_tests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mockTestId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @ManyToMany
    @JoinTable(
        name = "mock_test_listening_parts",
        joinColumns = @JoinColumn(name = "mock_test_id"),
        inverseJoinColumns = @JoinColumn(name = "part_id")
    )
    @OrderColumn(name = "part_order")
    @Builder.Default
    private List<ListeningPart> listeningParts = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "mock_test_reading_quizzes",
        joinColumns = @JoinColumn(name = "mock_test_id"),
        inverseJoinColumns = @JoinColumn(name = "quiz_id")
    )
    @OrderColumn(name = "passage_order")
    @Builder.Default
    private List<ReadingQuiz> readingQuizzes = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "mock_test_writing_prompts",
        joinColumns = @JoinColumn(name = "mock_test_id"),
        inverseJoinColumns = @JoinColumn(name = "prompt_id")
    )
    @OrderColumn(name = "prompt_order")
    @Builder.Default
    private List<WritingPrompt> writingPrompts = new ArrayList<>();

    @OneToMany(mappedBy = "mockTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionOrder ASC")
    @Builder.Default
    private List<MockTestSection> sections = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
