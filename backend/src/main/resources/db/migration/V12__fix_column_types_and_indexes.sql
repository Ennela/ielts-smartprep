-- ============================================================
-- V12: Fix column types + Add performance indexes
-- ============================================================

-- 1. Upgrade passage_text to MEDIUMTEXT (IELTS passages can be 1000-2000 words)
ALTER TABLE reading_quizzes MODIFY COLUMN passage_text MEDIUMTEXT NOT NULL;

-- 2. Upgrade options_json to JSON native type for better validation
ALTER TABLE reading_questions MODIFY COLUMN options_json JSON;

-- 3. Performance indexes for frequently queried history endpoints
CREATE INDEX idx_score_history_user_skill ON score_history(user_id, skill_type);
CREATE INDEX idx_reading_quizzes_user ON reading_quizzes(user_id);
CREATE INDEX idx_writing_submissions_user ON writing_submissions(user_id);
CREATE INDEX idx_listening_tests_user ON listening_tests(user_id);
