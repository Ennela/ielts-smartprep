-- ============================================================
-- V13: Add support for admin-provided reading quizzes
-- ============================================================

-- 1. Make user_id nullable in reading_quizzes to allow admin templates (which don't belong to a specific student)
ALTER TABLE reading_quizzes MODIFY COLUMN user_id BIGINT NULL;

-- 2. Add is_template column to distinguish templates from user test attempts
ALTER TABLE reading_quizzes ADD COLUMN is_template BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Add parent_template_id column to link user attempts to admin templates
ALTER TABLE reading_quizzes ADD COLUMN parent_template_id BIGINT NULL;

-- 4. Add foreign key from parent_template_id to quiz_id
ALTER TABLE reading_quizzes ADD CONSTRAINT fk_reading_quizzes_parent FOREIGN KEY (parent_template_id) REFERENCES reading_quizzes(quiz_id) ON DELETE SET NULL;

-- 5. Add index for performance when listing templates
CREATE INDEX idx_reading_quizzes_template ON reading_quizzes(is_template);
