-- Upgrade reading_questions to support all 8 IELTS question types

-- Widen question_type column for longer type names
ALTER TABLE reading_questions MODIFY COLUMN question_type VARCHAR(30) NOT NULL;

-- Add columns for matching/completion question types
ALTER TABLE reading_questions ADD COLUMN options_json TEXT NULL;
ALTER TABLE reading_questions ADD COLUMN word_limit INT NULL;
ALTER TABLE reading_questions ADD COLUMN group_label VARCHAR(100) NULL;
ALTER TABLE reading_questions ADD COLUMN group_id INT NULL;
ALTER TABLE reading_questions ADD COLUMN group_context TEXT NULL;

-- Update default total_questions from 5 to 13
ALTER TABLE reading_quizzes ALTER COLUMN total_questions SET DEFAULT 13;
