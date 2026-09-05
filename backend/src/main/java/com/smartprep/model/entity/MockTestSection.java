package com.smartprep.model.entity;

import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mock_test_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "mockTest")
public class MockTestSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_test_id", nullable = false)
    private MockTest mockTest;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "section_type", nullable = false, length = 20)
    private SkillType sectionType;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder;
}
