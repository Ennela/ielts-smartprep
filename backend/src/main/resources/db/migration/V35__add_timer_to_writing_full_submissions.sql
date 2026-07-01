-- Add timer tracking fields to writing_full_submissions for history display
ALTER TABLE writing_full_submissions
    ADD COLUMN time_spent_seconds INT NULL,
    ADD COLUMN time_spent_task1 INT NULL,
    ADD COLUMN time_spent_task2 INT NULL,
    ADD COLUMN auto_submitted TINYINT(1) DEFAULT 0;
