-- Widen group_label column in reading_questions to VARCHAR(255) to prevent truncation errors
ALTER TABLE reading_questions MODIFY COLUMN group_label VARCHAR(255) NULL;
