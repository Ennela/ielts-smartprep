-- V47: Let a score_history row say which mock test sitting it came from.
--
-- Full mock tests never wrote score_history, so the dashboard, score trends, weakness
-- analysis and the adaptive difficulty picker were all blind to them: a candidate who
-- only ever sat full tests saw an empty dashboard. They now write one row per graded
-- skill, and this column is what makes that safe to do more than once. Writing is graded
-- asynchronously and can be re-run after a failure, so without a link back to the
-- submission a retry would add a second WRITING row for the same sitting and double
-- count it everywhere.
--
-- Nullable: practice rows have no submission. SET NULL rather than CASCADE so that a
-- submission being removed can never take a candidate's history with it.
ALTER TABLE score_history
    ADD COLUMN mock_test_submission_id BIGINT NULL,
    ADD CONSTRAINT fk_sh_mock_test_submission
        FOREIGN KEY (mock_test_submission_id) REFERENCES mock_test_submissions(submission_id)
        ON DELETE SET NULL,
    ADD INDEX idx_sh_mock_test_submission (mock_test_submission_id);
