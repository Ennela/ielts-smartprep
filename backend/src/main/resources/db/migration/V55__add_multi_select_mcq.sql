-- V55: "Choose TWO letters, A-E" -- multiple-choice questions that take more than one answer.
--
-- In the exam such a task covers two (or three) question numbers, and each correct letter
-- the candidate chooses is worth one of them, in either order. There was no way to store
-- one, which is why V32 had to break two of them into four single-answer MCQs.
--
-- Stored without changing what a row is: still one question number, one mark, one correct
-- letter. The rows of the task share a group_id and all carry select_count = 2. The
-- candidate's choice ("B,D") is recorded on every row, and a row is correct when its letter
-- is among no more than select_count chosen letters. So choosing a right letter twice
-- scores it once, and choosing every letter scores nothing. Band, history and results keep
-- counting rows as before.
--
-- 1 everywhere else: an ordinary multiple-choice question takes one letter.
ALTER TABLE reading_questions
    ADD COLUMN select_count INT NOT NULL DEFAULT 1;

ALTER TABLE listening_questions
    ADD COLUMN select_count INT NOT NULL DEFAULT 1;
