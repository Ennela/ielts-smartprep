package com.smartprep.repository;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.enums.AudioStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ListeningPartRepository}.
 * Uses Testcontainers MySQL with Flyway migrations.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ListeningPartRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ListeningPartRepository listeningPartRepository;

    @BeforeEach
    void setUp() {
        // Clear any seeded data from Flyway to isolate tests
        listeningPartRepository.deleteAll();
        entityManager.flush();
    }

    @Test
    @DisplayName("findByPartNumberOrderByPartIdAsc — returns parts matching the part number")
    void findByPartNumber() {
        entityManager.persistAndFlush(buildPart(1, "Part 1A", "topic_a", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(1, "Part 1B", "topic_b", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(2, "Part 2A", "topic_c", AudioStatus.PENDING));

        List<ListeningPart> result = listeningPartRepository.findByPartNumberOrderByPartIdAsc(1);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ListeningPart::getPartNumber).containsOnly(1);
        // Ordered by partId ascending
        assertThat(result.get(0).getPartId()).isLessThan(result.get(1).getPartId());
    }

    @Test
    @DisplayName("findAllByOrderByPartNumberAscPartIdAsc — returns all parts ordered")
    void findAllOrdered() {
        entityManager.persistAndFlush(buildPart(2, "Part 2", "topic_x", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(1, "Part 1", "topic_y", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(3, "Part 3", "topic_z", AudioStatus.PENDING));

        List<ListeningPart> result = listeningPartRepository.findAllByOrderByPartNumberAscPartIdAsc();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ListeningPart::getPartNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("findByFilters — filters by audioStatus with pagination")
    void findByFilters_audioStatus() {
        for (int i = 0; i < 5; i++) {
            entityManager.persistAndFlush(buildPart(1, "Ready " + i, "topic", AudioStatus.READY));
        }
        for (int i = 0; i < 3; i++) {
            entityManager.persistAndFlush(buildPart(1, "Pending " + i, "topic", AudioStatus.PENDING));
        }

        Page<ListeningPart> readyPage = listeningPartRepository.findByFilters(
                AudioStatus.READY, null, PageRequest.of(0, 3));

        assertThat(readyPage.getContent()).hasSizeLessThanOrEqualTo(3);
        assertThat(readyPage.getContent()).allMatch(p -> p.getAudioStatus() == AudioStatus.READY);
        assertThat(readyPage.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("findByFilters — filters by topic")
    void findByFilters_topic() {
        entityManager.persistAndFlush(buildPart(1, "Campus Tour", "campus", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(1, "Library", "library", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(2, "Campus Life", "campus", AudioStatus.READY));

        Page<ListeningPart> page = listeningPartRepository.findByFilters(
                null, "campus", PageRequest.of(0, 10));

        assertThat(page.getContent()).allMatch(p -> "campus".equals(p.getTopic()));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByFilters — null filters return all parts")
    void findByFilters_noFilter() {
        entityManager.persistAndFlush(buildPart(1, "A", "t1", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(2, "B", "t2", AudioStatus.PENDING));

        Page<ListeningPart> page = listeningPartRepository.findByFilters(
                null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByAudioStatus — returns parts with matching status")
    void findByAudioStatus() {
        entityManager.persistAndFlush(buildPart(1, "Ready 1", "t", AudioStatus.READY));
        entityManager.persistAndFlush(buildPart(1, "Failed 1", "t", AudioStatus.FAILED));
        entityManager.persistAndFlush(buildPart(2, "Failed 2", "t", AudioStatus.FAILED));

        List<ListeningPart> failed = listeningPartRepository.findByAudioStatus(AudioStatus.FAILED);

        assertThat(failed).hasSize(2);
        assertThat(failed).allMatch(p -> p.getAudioStatus() == AudioStatus.FAILED);
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    private ListeningPart buildPart(int partNumber, String title, String topic, AudioStatus audioStatus) {
        return ListeningPart.builder()
                .partNumber(partNumber)
                .title(title)
                .topic(topic)
                .audioUrl("https://example.com/audio/" + title.replaceAll("\\s+", "_") + ".mp3")
                .audioStatus(audioStatus)
                .transcriptText("Sample transcript for " + title)
                .durationSeconds(120)
                .createdBy("TEST")
                .build();
    }
}
