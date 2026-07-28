-- V41: Make admin deletion recoverable and prevent shared/history data from being
-- removed by a future physical delete.

ALTER TABLE reading_quizzes
    ADD COLUMN deleted_at DATETIME NULL,
    ADD INDEX idx_reading_quizzes_deleted_at (deleted_at);

ALTER TABLE listening_parts
    ADD COLUMN deleted_at DATETIME NULL,
    ADD INDEX idx_listening_parts_deleted_at (deleted_at);

ALTER TABLE writing_prompts
    ADD COLUMN deleted_at DATETIME NULL,
    ADD INDEX idx_writing_prompts_deleted_at (deleted_at);

ALTER TABLE mock_tests
    ADD COLUMN deleted_at DATETIME NULL,
    ADD INDEX idx_mock_tests_deleted_at (deleted_at);

-- Shared content must not silently disappear from mock-test compositions.
ALTER TABLE mock_test_listening_parts
    DROP FOREIGN KEY fk_mtlp_part_id;

ALTER TABLE mock_test_listening_parts
    ADD CONSTRAINT fk_mtlp_part_id
        FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE RESTRICT;

ALTER TABLE mock_test_reading_quizzes
    DROP FOREIGN KEY fk_mtrq_quiz_id;

ALTER TABLE mock_test_reading_quizzes
    ADD CONSTRAINT fk_mtrq_quiz_id
        FOREIGN KEY (quiz_id) REFERENCES reading_quizzes(quiz_id) ON DELETE RESTRICT;

ALTER TABLE mock_test_writing_prompts
    DROP FOREIGN KEY fk_mtwp_prompt_id;

ALTER TABLE mock_test_writing_prompts
    ADD CONSTRAINT fk_mtwp_prompt_id
        FOREIGN KEY (prompt_id) REFERENCES writing_prompts(prompt_id) ON DELETE RESTRICT;

-- Preserve user work/history if a content record is ever physically purged.
SET @fk_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'writing_submissions'
      AND COLUMN_NAME = 'prompt_id'
      AND REFERENCED_TABLE_NAME = 'writing_prompts'
    LIMIT 1
);
SET @sql = CONCAT(
    'ALTER TABLE writing_submissions DROP FOREIGN KEY `',
    @fk_name,
    '`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE writing_submissions
    ADD CONSTRAINT fk_writing_submissions_prompt_id
        FOREIGN KEY (prompt_id) REFERENCES writing_prompts(prompt_id) ON DELETE RESTRICT;

SET @fk_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'listening_test_parts'
      AND COLUMN_NAME = 'part_id'
      AND REFERENCED_TABLE_NAME = 'listening_parts'
    LIMIT 1
);
SET @sql = CONCAT(
    'ALTER TABLE listening_test_parts DROP FOREIGN KEY `',
    @fk_name,
    '`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE listening_test_parts
    ADD CONSTRAINT fk_listening_test_parts_part_id
        FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE RESTRICT;
