package com.smartprep.model.entity;

import com.smartprep.model.enums.TestMode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listening_tests")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ListeningTest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long testId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TestMode testMode;

    @Column(precision = 2, scale = 1)
    private BigDecimal score;

    private Integer totalQuestions;
    private Integer correctAnswers;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ListeningTestPart> testParts = new ArrayList<>();

    @PrePersist
    protected void onCreate() { submittedAt = LocalDateTime.now(); }
}
