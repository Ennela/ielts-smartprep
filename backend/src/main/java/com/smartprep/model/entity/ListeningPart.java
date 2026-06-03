package com.smartprep.model.entity;

import com.smartprep.model.enums.AudioStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listening_parts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ListeningPart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long partId;

    @Column(nullable = false)
    private Integer partNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String topic;

    @Column(nullable = false)
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private AudioStatus audioStatus = AudioStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String transcriptText;

    private Integer durationSeconds;

    @OneToMany(mappedBy = "part", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ListeningQuestion> questions = new ArrayList<>();
}
