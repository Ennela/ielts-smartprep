-- Add timer tracking fields to score_history for displaying time spent on results/history
ALTER TABLE score_history
    ADD COLUMN time_spent_seconds INT NULL,
    ADD COLUMN time_spent_task1 INT NULL,
    ADD COLUMN time_spent_task2 INT NULL,
    ADD COLUMN auto_submitted TINYINT(1) DEFAULT 0;
