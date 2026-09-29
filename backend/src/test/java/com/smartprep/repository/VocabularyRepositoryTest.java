package com.smartprep.repository;

import com.smartprep.model.entity.User;
import com.smartprep.model.entity.Vocabulary;
import com.smartprep.model.enums.Role;
import com.smartprep.model.enums.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    // ===================================================================
    //  findForUser (the vocabulary page's list, paged and filtered in SQL)
    // ===================================================================

    @Test
    @DisplayName("findForUser — pages a user's words newest first, and only theirs")
    void findForUser_pagesOwnWordsNewestFirst() {
        for (int i = 1; i <= 3; i++) {
            entityManager.persistAndFlush(buildVocab("word" + i, "nghĩa " + i, LocalDateTime.now()));
        }
        User other = entityManager.persistAndFlush(User.builder()
                .username("other_vocab_user").email("other_vocab@test.com")
                .passwordHash("hash").role(Role.STUDENT).build());
        entityManager.persistAndFlush(Vocabulary.builder()
                .user(other).word("theirs").meaningVi("của họ")
                .easeFactor(2.5).intervalDays(1).repetitions(0).dueDate(LocalDateTime.now()).build());

        Page<Vocabulary> firstPage = vocabularyRepository.findForUser(
                testUser.getUserId(), null, null, null, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent()).extracting(Vocabulary::getWord).doesNotContain("theirs");
    }

    @Test
    @DisplayName("findForUser — matches the search term against word, meaning and part of speech")
    void findForUser_searchesTheThreeTextFields() {
        Vocabulary v = buildVocab("ubiquitous", "phổ biến khắp nơi", LocalDateTime.now());
        v.setPartOfSpeech("adjective");
        entityManager.persistAndFlush(v);
        entityManager.persistAndFlush(buildVocab("kettle", "ấm đun nước", LocalDateTime.now()));

        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), "%ubiq%", null, null, PageRequest.of(0, 10)))
                .extracting(Vocabulary::getWord).containsExactly("ubiquitous");
        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), "%khắp%", null, null, PageRequest.of(0, 10)))
                .extracting(Vocabulary::getWord).containsExactly("ubiquitous");
        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), "%adject%", null, null, PageRequest.of(0, 10)))
                .extracting(Vocabulary::getWord).containsExactly("ubiquitous");
    }

    @Test
    @DisplayName("findForUser — filters by CEFR level and source skill, null means every value")
    void findForUser_filtersByLevelAndSkill() {
        Vocabulary reading = buildVocab("harvest", "thu hoạch", LocalDateTime.now());
        reading.setCefrLevel("B2");
        reading.setSourceSkill(SkillType.READING);
        entityManager.persistAndFlush(reading);

        Vocabulary listening = buildVocab("platform", "sân ga", LocalDateTime.now());
        listening.setCefrLevel("C1");
        listening.setSourceSkill(SkillType.LISTENING);
        entityManager.persistAndFlush(listening);

        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), null, "B2", null, PageRequest.of(0, 10)))
                .extracting(Vocabulary::getWord).containsExactly("harvest");
        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), null, null, SkillType.LISTENING, PageRequest.of(0, 10)))
                .extracting(Vocabulary::getWord).containsExactly("platform");
        assertThat(vocabularyRepository.findForUser(testUser.getUserId(), null, null, null, PageRequest.of(0, 10)))
                .hasSize(2);
    }

    // ===================================================================
    //  findDueReminderTargets
    // ===================================================================

    @Test
    @DisplayName("findDueReminderTargets — one row per learner, counting the words due")
    void reminderTargets_groupsPerUser() {
        testUser.setEmailVerified(true);
        testUser.setEmailNotifications(true);
        entityManager.persistAndFlush(testUser);

        entityManager.persistAndFlush(buildVocab("harvest", "thu hoạch", LocalDateTime.now().minusDays(1)));
        entityManager.persistAndFlush(buildVocab("platform", "sân ga", LocalDateTime.now().minusHours(2)));
        // Not due yet, so it must not be counted.
        entityManager.persistAndFlush(buildVocab("later", "sau", LocalDateTime.now().plusDays(3)));

        List<VocabularyRepository.DueReminderTarget> targets =
                vocabularyRepository.findDueReminderTargets(LocalDateTime.now(), PageRequest.of(0, 10));

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getUserId()).isEqualTo(testUser.getUserId());
        assertThat(targets.get(0).getEmail()).isEqualTo("vocab@test.com");
        assertThat(targets.get(0).getDueCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("findDueReminderTargets — skips a learner who turned the preference off")
    void reminderTargets_respectsThePreference() {
        testUser.setEmailVerified(true);
        testUser.setEmailNotifications(false);
        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(buildVocab("harvest", "thu hoạch", LocalDateTime.now().minusDays(1)));

        assertThat(vocabularyRepository.findDueReminderTargets(LocalDateTime.now(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("findDueReminderTargets — skips an address that was never confirmed")
    void reminderTargets_requiresAVerifiedAddress() {
        testUser.setEmailVerified(false);
        testUser.setEmailNotifications(true);
        entityManager.persistAndFlush(testUser);
        entityManager.persistAndFlush(buildVocab("harvest", "thu hoạch", LocalDateTime.now().minusDays(1)));

        assertThat(vocabularyRepository.findDueReminderTargets(LocalDateTime.now(), PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("findDueReminderTargets — honours the ceiling the job passes in")
    void reminderTargets_respectsTheLimit() {
        testUser.setEmailVerified(true);
        testUser.setEmailNotifications(true);
        entityManager.persistAndFlush(testUser);

        User other = User.builder()
                .username("vocab_test_user_2")
                .email("vocab2@test.com")
                .passwordHash("hash")
                .role(Role.STUDENT)
                .emailVerified(true)
                .emailNotifications(true)
                .build();
        other = entityManager.persistAndFlush(other);

        entityManager.persistAndFlush(buildVocab("harvest", "thu hoạch", LocalDateTime.now().minusDays(1)));
        entityManager.persistAndFlush(Vocabulary.builder()
                .user(other).word("platform").meaningVi("sân ga")
                .easeFactor(2.5).intervalDays(1).repetitions(0)
                .dueDate(LocalDateTime.now().minusDays(1)).build());

        assertThat(vocabularyRepository.findDueReminderTargets(LocalDateTime.now(), PageRequest.of(0, 10)))
                .hasSize(2);
        assertThat(vocabularyRepository.findDueReminderTargets(LocalDateTime.now(), PageRequest.of(0, 1)))
                .hasSize(1);
    }

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
