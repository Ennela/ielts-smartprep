-- V34: Add verified flag to reading and listening questions to support QA flow
ALTER TABLE reading_questions ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE listening_questions ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
