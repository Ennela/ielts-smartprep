package com.smartprep.model.entity;

import com.smartprep.model.enums.EssayType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "writing_prompts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WritingPrompt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promptId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String promptText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EssayType essayType;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
