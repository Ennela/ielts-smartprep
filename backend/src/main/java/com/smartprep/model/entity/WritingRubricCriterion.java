package com.smartprep.model.entity;

import com.smartprep.model.enums.RubricCriterionName;
import com.smartprep.model.enums.WritingTaskType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "writing_rubric_criteria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WritingRubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "task_type", nullable = false, length = 10)
    private WritingTaskType taskType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "criterion_name", nullable = false, length = 30)
    private RubricCriterionName criterionName;

    @Column(name = "band_descriptors", columnDefinition = "JSON", nullable = false)
    private String bandDescriptors;

    @Column(name = "rubric_version", nullable = false, length = 20)
    @Builder.Default
    private String rubricVersion = "IELTS_2026_V1";

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
