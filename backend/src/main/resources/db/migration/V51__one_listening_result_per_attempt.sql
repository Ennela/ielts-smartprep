-- V51: One graded Listening result per sitting.
--
-- POST /listening/submit creates a new listening_tests row on every call, and its response
-- contains every correct answer. With nothing tying a result to the sitting that produced
-- it, a candidate could submit blanks, read the answers out of the response and submit
-- again for band 9.0. The service already refused a second submit when the request named
-- its attempt, but attemptId was optional, so leaving it out bypassed the check entirely.
--
-- The service check alone also could not close the race between two concurrent submits
-- that both read the attempt as unsubmitted. This constraint does, the same way V44 does
-- for mock test sessions.
--
-- Nullable because rows written before this migration have no attempt to point at. MySQL
-- allows any number of NULLs under a UNIQUE constraint, so those rows are unaffected.

ALTER TABLE listening_tests
    ADD COLUMN attempt_id BIGINT NULL;

ALTER TABLE listening_tests
    ADD CONSTRAINT uq_listening_tests_attempt UNIQUE (attempt_id),
    ADD CONSTRAINT fk_listening_tests_attempt FOREIGN KEY (attempt_id)
        REFERENCES exam_attempts (attempt_id) ON DELETE SET NULL;
