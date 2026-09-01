package com.smartprep.model.entity;

import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.WritingTaskType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "writing_prompts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WritingPrompt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promptId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String promptText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private EssayType essayType;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "visual_data", columnDefinition = "TEXT")
    private String visualData;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_status", nullable = false, length = 20)
    @Builder.Default
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "seed_key", length = 100)
    private String seedKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "task_type", nullable = false, length = 10)
    private WritingTaskType taskType;

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
