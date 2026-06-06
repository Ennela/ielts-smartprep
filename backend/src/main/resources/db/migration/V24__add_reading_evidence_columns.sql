-- V24: Add reading evidence columns to store exact answer location in passage
ALTER TABLE reading_questions ADD COLUMN evidence_text TEXT NULL;
ALTER TABLE reading_questions ADD COLUMN evidence_offset INT NULL;
ALTER TABLE reading_questions ADD COLUMN evidence_length INT NULL;

ALTER TABLE user_answers ADD COLUMN evidence_text TEXT NULL;
ALTER TABLE user_answers ADD COLUMN evidence_offset INT NULL;
ALTER TABLE user_answers ADD COLUMN evidence_length INT NULL;
