-- V36: Fix FK constraints on mock test join tables to allow cascade delete
-- When deleting a listening_part, reading_quiz, or writing_prompt,
-- the join table rows should be automatically removed (unlink from mock test).

-- 1. mock_test_listening_parts: Add ON DELETE CASCADE for part_id FK
SET @fk_name = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mock_test_listening_parts'
      AND COLUMN_NAME = 'part_id'
      AND REFERENCED_TABLE_NAME = 'listening_parts'
    LIMIT 1
);

SET @sql = CONCAT('ALTER TABLE mock_test_listening_parts DROP FOREIGN KEY `', @fk_name, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE mock_test_listening_parts
    ADD CONSTRAINT fk_mtlp_part_id
    FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE CASCADE;

-- 2. mock_test_reading_quizzes: Add ON DELETE CASCADE for quiz_id FK
SET @fk_name = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mock_test_reading_quizzes'
      AND COLUMN_NAME = 'quiz_id'
      AND REFERENCED_TABLE_NAME = 'reading_quizzes'
    LIMIT 1
);

SET @sql = CONCAT('ALTER TABLE mock_test_reading_quizzes DROP FOREIGN KEY `', @fk_name, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE mock_test_reading_quizzes
    ADD CONSTRAINT fk_mtrq_quiz_id
    FOREIGN KEY (quiz_id) REFERENCES reading_quizzes(quiz_id) ON DELETE CASCADE;

-- 3. mock_test_writing_prompts: Add ON DELETE CASCADE for prompt_id FK
SET @fk_name = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mock_test_writing_prompts'
      AND COLUMN_NAME = 'prompt_id'
      AND REFERENCED_TABLE_NAME = 'writing_prompts'
    LIMIT 1
);

SET @sql = CONCAT('ALTER TABLE mock_test_writing_prompts DROP FOREIGN KEY `', @fk_name, '`');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE mock_test_writing_prompts
    ADD CONSTRAINT fk_mtwp_prompt_id
    FOREIGN KEY (prompt_id) REFERENCES writing_prompts(prompt_id) ON DELETE CASCADE;
