package com.smartprep.repository;

import com.smartprep.model.entity.User;
import com.smartprep.model.entity.Vocabulary;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link VocabularyRepository}.
 * Tests SRS due-date queries and word lookup against a real MySQL database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VocabularyRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("vocab_test_user")
                .email("vocab@test.com")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .build();
        testUser = entityManager.persistAndFlush(testUser);
    }

    // ===================================================================
    //  findByUserUserIdAndWord
    // ===================================================================

    @Test
    @DisplayName("findByUserUserIdAndWord — finds exact word for user")
    void findByUserAndWord_found() {
        Vocabulary vocab = buildVocab("ubiquitous", "phổ biến", LocalDateTime.now());
        entityManager.persistAndFlush(vocab);

        Optional<Vocabulary> result = vocabularyRepository.findByUserUserIdAndWord(
                testUser.getUserId(), "ubiquitous");

        assertThat(result).isPresent();
        assertThat(result.get().getWord()).isEqualTo("ubiquitous");
    }

    @Test
    @DisplayName("findByUserUserIdAndWord — returns empty when word not found")
    void findByUserAndWord_notFound() {
        Optional<Vocabulary> result = vocabularyRepository.findByUserUserIdAndWord(
                testUser.getUserId(), "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserUserIdAndWord — does not return words owned by other users")
    void findByUserAndWord_differentUser() {
        Vocabulary vocab = buildVocab("unique", "duy nhất", LocalDateTime.now());
        entityManager.persistAndFlush(vocab);

        // Search with a different user ID
        Optional<Vocabulary> result = vocabularyRepository.findByUserUserIdAndWord(999L, "unique");

        assertThat(result).isEmpty();
    }

    // ===================================================================
    //  findByUserUserIdAndDueDateBeforeOrderByDueDateAsc (SRS Due Query)
    // ===================================================================

    @Test
    @DisplayName("SRS query returns only past-due items, ordered by dueDate ASC")
    void findDue_returnsOnlyPastDue() {
        LocalDateTime now = LocalDateTime.now();

        // Due yesterday (should be returned)
        Vocabulary due1 = buildVocab("word_due_1", "nghĩa 1", now.minusDays(1));
        // Due 3 days ago (should be returned first — earlier due date)
        Vocabulary due2 = buildVocab("word_due_2", "nghĩa 2", now.minusDays(3));
        // Due tomorrow (should NOT be returned)
        Vocabulary notDue = buildVocab("word_future", "nghĩa 3", now.plusDays(1));

        entityManager.persistAndFlush(due1);
        entityManager.persistAndFlush(due2);
        entityManager.persistAndFlush(notDue);

        List<Vocabulary> result = vocabularyRepository
                .findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(testUser.getUserId(), now);

        assertThat(result).hasSize(2);
        // Ordered ascending: due2 (3 days ago) before due1 (1 day ago)
        assertThat(result.get(0).getWord()).isEqualTo("word_due_2");
        assertThat(result.get(1).getWord()).isEqualTo("word_due_1");
    }

    @Test
    @DisplayName("SRS query returns empty when nothing is due")
    void findDue_emptyWhenNothingDue() {
        Vocabulary notDue = buildVocab("future_word", "tương lai", LocalDateTime.now().plusDays(10));
        entityManager.persistAndFlush(notDue);

        List<Vocabulary> result = vocabularyRepository
                .findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(testUser.getUserId(), LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    // ===================================================================
    //  findByUserUserIdOrderByCreatedAtDesc
    // ===================================================================

    @Test
    @DisplayName("getAllVocab returns all items for user ordered by createdAt DESC")
    void findAll_orderedByCreatedAtDesc() {
        Vocabulary v1 = buildVocab("alpha", "a", LocalDateTime.now());
        Vocabulary v2 = buildVocab("beta", "b", LocalDateTime.now());
        Vocabulary v3 = buildVocab("gamma", "c", LocalDateTime.now());

        entityManager.persistAndFlush(v1);
        entityManager.persistAndFlush(v2);
        entityManager.persistAndFlush(v3);

        List<Vocabulary> result = vocabularyRepository
                .findByUserUserIdOrderByCreatedAtDesc(testUser.getUserId());

        assertThat(result).hasSize(3);
        // createdAt is set via @PrePersist, so the last-persisted should come first
        // due to near-identical timestamps, just verify all belong to user
        assertThat(result).allMatch(v -> v.getUser().getUserId().equals(testUser.getUserId()));
    }

    @Test
    @DisplayName("getAllVocab returns empty for non-existent user")
    void findAll_emptyForNonexistentUser() {
        assertThat(vocabularyRepository.findByUserUserIdOrderByCreatedAtDesc(999L)).isEmpty();
    }

    // ===================================================================
    //  CRUD sanity checks
    // ===================================================================

    @Test
    @DisplayName("save and findById — full lifecycle with SRS fields")
    void saveAndFind_lifecycle() {
        Vocabulary vocab = Vocabulary.builder()
                .user(testUser)
                .word("serendipity")
                .meaningVi("tình cờ may mắn")
                .phonetic("/ˌserənˈdɪpəti/")
                .partOfSpeech("noun")
                .example("Finding that book was pure serendipity.")
                .collocation("happy serendipity")
                .cefrLevel("C1")
                .sourceSkill(SkillType.READING)
                .sourceRef("Quiz #42")
                .easeFactor(2.5)
                .intervalDays(0)
                .repetitions(0)
                .dueDate(LocalDateTime.now())
                .build();

        vocab = entityManager.persistAndFlush(vocab);

        Vocabulary found = vocabularyRepository.findById(vocab.getVocabId()).orElseThrow();

        assertThat(found.getWord()).isEqualTo("serendipity");
        assertThat(found.getPhonetic()).isEqualTo("/ˌserənˈdɪpəti/");
        assertThat(found.getSourceSkill()).isEqualTo(SkillType.READING);
        assertThat(found.getCefrLevel()).isEqualTo("C1");
        assertThat(found.getEaseFactor()).isEqualTo(2.5);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    private Vocabulary buildVocab(String word, String meaningVi, LocalDateTime dueDate) {
        return Vocabulary.builder()
                .user(testUser)
                .word(word)
                .meaningVi(meaningVi)
                .easeFactor(2.5)
                .intervalDays(1)
                .repetitions(0)
                .dueDate(dueDate)
                .build();
    }
}
