-- V59: Speaking Part 1 and Part 3 are answered one question at a time.
--
-- As in the test, the examiner asks each question and the candidate answers it straight
-- away, so a Part 1 or Part 3 submission now holds one recording per question, in order.
-- Gemini still grades the set in one call (one set of four bands) and adds a transcript and
-- a short comment for each answer.
--
-- Part 2 stays one recording on the submission row itself. Part 1/3 submissions keep their
-- recordings here, so the submission's own audio columns become optional; the one earlier
-- Part 1 answer recorded in a single take keeps its recording where it was.
CREATE TABLE IF NOT EXISTS speaking_answers (
    answer_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id    BIGINT NOT NULL,
    question_index   INT NOT NULL,
    audio_key        VARCHAR(255) NOT NULL,
    audio_mime_type  VARCHAR(50) NOT NULL,
    duration_seconds INT NOT NULL,
    transcript       TEXT NULL,
    comment          TEXT NULL,
    CONSTRAINT fk_speaking_answer_sub FOREIGN KEY (submission_id) REFERENCES speaking_submissions(submission_id),
    CONSTRAINT uq_speaking_answer UNIQUE (submission_id, question_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE speaking_submissions
    MODIFY audio_key VARCHAR(255) NULL,
    MODIFY audio_mime_type VARCHAR(50) NULL;
