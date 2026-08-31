-- V44: One submission per mock test session, and a usable section start time.
--
-- mock_test_submissions.session_id had no unique constraint (V14), so two concurrent
-- submits for the same session could both pass the IN_PROGRESS status check and each
-- write a submission row, a listening test and an AI grading job. The service-level
-- guard cannot close that race on its own; the database has to.
--
-- No deduplication is performed here on purpose. If a database already contains
-- duplicate session_id values this migration will fail rather than silently discard a
-- user's graded submission — resolve those rows manually, then re-run.
ALTER TABLE mock_test_submissions
    ADD CONSTRAINT uq_mock_test_submissions_session UNIQUE (session_id);

-- Server-side section timing is derived from section_started_at. The column has
-- DEFAULT CURRENT_TIMESTAMP (V14) and the entity sets it in @PrePersist, so it should
-- never be null; this backfills any legacy row that predates that so an in-flight
-- session cannot end up with an unbounded deadline.
UPDATE mock_test_sessions
SET section_started_at = started_at
WHERE section_started_at IS NULL;
