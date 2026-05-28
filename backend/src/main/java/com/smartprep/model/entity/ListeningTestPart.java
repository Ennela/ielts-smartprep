package com.smartprep.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "listening_test_parts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ListeningTestPart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id", nullable = false)
    private ListeningTest test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private ListeningPart part;

    @Column(columnDefinition = "JSON")
    private String userAnswersJson;
}
