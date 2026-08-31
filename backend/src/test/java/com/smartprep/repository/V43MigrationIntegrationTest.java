package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingRubricCriterion;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.RubricCriterionName;
import com.smartprep.model.enums.Topic;
import com.smartprep.model.enums.WritingTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying V43 migration applies cleanly and constraints work.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class V43MigrationIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WritingRubricCriterionRepository rubricRepository;

    @Test
    @DisplayName("V43 migration applies cleanly — Flyway startup succeeds with all V1-V43 migrations")
    void migrationAppliesCleanly() {
        // If we reach this point, Flyway has already applied V1-V43 successfully.
        // Verify the new table exists by saving and retrieving a rubric criterion.
        WritingRubricCriterion criterion = WritingRubricCriterion.builder()
                .taskType(WritingTaskType.TASK_1)
                .criterionName(RubricCriterionName.TASK_ACHIEVEMENT)
                .bandDescriptors("{\"9\":\"Fully satisfies all requirements\"}")
                .build();
        WritingRubricCriterion saved = entityManager.persistAndFlush(criterion);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRubricVersion()).isEqualTo("IELTS_2026_V1");
    }

    @Test
    @DisplayName("seed_key UNIQUE constraint: duplicate seed_key rejected, multiple NULLs allowed")
    void seedKeyUniqueConstraint() {
        // Two rows with NULL seed_key — should succeed
        ReadingQuiz quiz1 = ReadingQuiz.builder()
                .topic(Topic.TECHNOLOGY)
                .difficulty(Difficulty.PASSAGE_1)
                .passageText("First quiz with null seed_key")
                .isTemplate(true)
                .build();
        ReadingQuiz quiz2 = ReadingQuiz.builder()
                .topic(Topic.SCIENCE)
                .difficulty(Difficulty.PASSAGE_2)
                .passageText("Second quiz with null seed_key")
                .isTemplate(true)
                .build();
        entityManager.persistAndFlush(quiz1);
        entityManager.persistAndFlush(quiz2);
        assertThat(quiz1.getSeedKey()).isNull();
        assertThat(quiz2.getSeedKey()).isNull();

        // Set seed_key on one
        quiz1.setSeedKey("cam19_reading_t1_p1");
        entityManager.persistAndFlush(quiz1);

        // Duplicate seed_key — must fail
        quiz2.setSeedKey("cam19_reading_t1_p1");
        assertThatThrownBy(() -> entityManager.persistAndFlush(quiz2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("writing_rubric_criteria composite UNIQUE: duplicate (task_type, criterion_name, rubric_version) rejected")
    void rubricCompositeUniqueConstraint() {
        WritingRubricCriterion c1 = WritingRubricCriterion.builder()
                .taskType(WritingTaskType.TASK_2)
                .criterionName(RubricCriterionName.COHERENCE_COHESION)
                .bandDescriptors("{\"5\":\"Adequate cohesion\"}")
                .build();
        entityManager.persistAndFlush(c1);

        // Same composite key — must fail
        WritingRubricCriterion c2 = WritingRubricCriterion.builder()
                .taskType(WritingTaskType.TASK_2)
                .criterionName(RubricCriterionName.COHERENCE_COHESION)
                .bandDescriptors("{\"5\":\"Different description\"}")
                .build();
        assertThatThrownBy(() -> entityManager.persistAndFlush(c2))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("writing_rubric_criteria allows different criterion_name with same task_type and version")
    void rubricDifferentCriterionAllowed() {
        WritingRubricCriterion c1 = WritingRubricCriterion.builder()
                .taskType(WritingTaskType.TASK_1)
                .criterionName(RubricCriterionName.LEXICAL_RESOURCE)
                .bandDescriptors("{\"7\":\"Good vocabulary range\"}")
                .build();
        WritingRubricCriterion c2 = WritingRubricCriterion.builder()
                .taskType(WritingTaskType.TASK_1)
                .criterionName(RubricCriterionName.GRAMMAR_ACCURACY)
                .bandDescriptors("{\"7\":\"Good grammatical control\"}")
                .build();
        entityManager.persistAndFlush(c1);
        entityManager.persistAndFlush(c2);
        assertThat(rubricRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}
