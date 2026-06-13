-- V25: Add module_type to reading_quizzes and score_history tables
ALTER TABLE reading_quizzes ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
ALTER TABLE score_history ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
