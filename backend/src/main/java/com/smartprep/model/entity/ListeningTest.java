package com.smartprep.model.entity;

import com.smartprep.model.enums.TestMode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private TestMode testMode;

    @Column(precision = 2, scale = 1)
    private BigDecimal score;

    private Integer totalQuestions;
    private Integer correctAnswers;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /**
     * The sitting this result grades. Unique in the database (V51), so one sitting can
     * produce one result and no more. Null only on rows written before that migration.
     */
    @Column(name = "attempt_id", updatable = false)
    private Long attemptId;

    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ListeningTestPart> testParts = new ArrayList<>();

    @PrePersist
    protected void onCreate() { submittedAt = LocalDateTime.now(); }
}
