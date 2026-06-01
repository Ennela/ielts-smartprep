package com.smartprep.model.entity;

import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mock_test_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockTestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_test_id", nullable = false)
    private MockTest mockTest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_section", nullable = false, length = 20)
    private SkillType currentSection;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime sectionStartedAt;

    @Column(nullable = false)
    private Integer timeRemainingSeconds;

    @Column(columnDefinition = "LONGTEXT")
    private String progressJson;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        sectionStartedAt = LocalDateTime.now();
        lastSyncedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastSyncedAt = LocalDateTime.now();
    }
}
