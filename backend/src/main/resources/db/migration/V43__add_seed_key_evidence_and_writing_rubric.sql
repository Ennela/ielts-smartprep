-- V43: Add seed_key, distractor evidence, task_type, and writing rubric criteria
-- Schema-only migration — no real data seeded.

-- === 1. seed_key: idempotent reseed support ===
ALTER TABLE reading_quizzes ADD COLUMN seed_key VARCHAR(100) NULL;
ALTER TABLE listening_parts  ADD COLUMN seed_key VARCHAR(100) NULL;
ALTER TABLE writing_prompts  ADD COLUMN seed_key VARCHAR(100) NULL;
ALTER TABLE mock_tests       ADD COLUMN seed_key VARCHAR(100) NULL;

ALTER TABLE reading_quizzes ADD UNIQUE KEY uq_reading_quizzes_seed_key (seed_key);
ALTER TABLE listening_parts  ADD UNIQUE KEY uq_listening_parts_seed_key (seed_key);
ALTER TABLE writing_prompts  ADD UNIQUE KEY uq_writing_prompts_seed_key (seed_key);
ALTER TABLE mock_tests       ADD UNIQUE KEY uq_mock_tests_seed_key (seed_key);

-- === 2. Distractor rationale (Tutor Agent) ===
ALTER TABLE question_options ADD COLUMN distractor_rationale TEXT NULL;

-- === 3. Evidence paragraph index (Tutor Agent) ===
-- evidence_text, evidence_offset, evidence_length already exist since V24
ALTER TABLE reading_questions ADD COLUMN evidence_paragraph_index INT NULL;

-- === 4. task_type on writing_prompts ===
ALTER TABLE writing_prompts ADD COLUMN task_type VARCHAR(10) NULL;

UPDATE writing_prompts SET task_type = 'TASK_1'
WHERE essay_type IN ('LINE_GRAPH','BAR_CHART','PIE_CHART','TABLE','MAP','DIAGRAM','LETTER');

UPDATE writing_prompts SET task_type = 'TASK_2'
WHERE essay_type IN ('OPINION','DISCUSSION','CAUSE_AND_EFFECT','PROBLEM_AND_SOLUTION',
                     'ADVANTAGES_DISADVANTAGES','TWO_PART_QUESTION');

ALTER TABLE writing_prompts MODIFY COLUMN task_type VARCHAR(10) NOT NULL;

-- === 5. Writing rubric criteria (Scoring Agent) ===
CREATE TABLE writing_rubric_criteria (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type        VARCHAR(10)  NOT NULL,
    criterion_name   VARCHAR(30)  NOT NULL,
    band_descriptors JSON         NOT NULL,
    rubric_version   VARCHAR(20)  NOT NULL DEFAULT 'IELTS_2026_V1',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_task_criterion_version (task_type, criterion_name, rubric_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
