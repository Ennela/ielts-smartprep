-- V48: Give the mock tests sat before V47 the score_history rows they would have today.
--
-- V47 made a full mock test record one score_history row per graded skill, but only for
-- sittings from that point on. Everything sat earlier stayed invisible to the dashboard,
-- the trend chart and the weakness card. This writes those rows, dated at the moment the
-- test was submitted so the trend places them where they belong, and links each to its
-- submission exactly as the live path does -- so a later retry of a writing grade sees
-- the row and does not add a second one.
--
-- Listening and Reading come from every submission that has a score for them, whatever
-- its status: the live path records them at submit time, before writing is graded.
-- Writing comes only from COMPLETED submissions; anything else carries the placeholder
-- zero, not a band. Reading's module_type is the paper's, falling back to ACADEMIC.
--
-- What cannot be reconstructed here: per-question user_answers. Those are built from the
-- session's progress JSON against the paper's questions at grading time; doing that in
-- SQL is not worth the risk, so the review page shows these rows without a question
-- breakdown. Every insert is guarded by NOT EXISTS, so re-running the statement -- or a
-- row the live path has already written -- changes nothing.

INSERT INTO score_history
    (user_id, skill_type, score, difficulty, module_type, recorded_at, auto_submitted, mock_test_submission_id)
SELECT s.user_id, 'LISTENING', s.listening_score, 'MOCK_TEST', 'ACADEMIC', s.submitted_at, 0, s.submission_id
FROM mock_test_submissions s
WHERE s.listening_score IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM score_history h
      WHERE h.mock_test_submission_id = s.submission_id AND h.skill_type = 'LISTENING');

INSERT INTO score_history
    (user_id, skill_type, score, difficulty, module_type, recorded_at, auto_submitted, mock_test_submission_id)
SELECT s.user_id, 'READING', s.reading_score, 'MOCK_TEST',
       COALESCE((SELECT rq.module_type
                 FROM mock_test_reading_quizzes m
                 JOIN reading_quizzes rq ON rq.quiz_id = m.quiz_id
                 WHERE m.mock_test_id = s.mock_test_id
                 ORDER BY m.passage_order
                 LIMIT 1), 'ACADEMIC'),
       s.submitted_at, 0, s.submission_id
FROM mock_test_submissions s
WHERE s.reading_score IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM score_history h
      WHERE h.mock_test_submission_id = s.submission_id AND h.skill_type = 'READING');

INSERT INTO score_history
    (user_id, skill_type, score, difficulty, module_type, recorded_at, auto_submitted, mock_test_submission_id)
SELECT s.user_id, 'WRITING', s.writing_score, 'MOCK_TEST', 'ACADEMIC', s.submitted_at, 0, s.submission_id
FROM mock_test_submissions s
WHERE s.status = 'COMPLETED'
  AND s.writing_score IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM score_history h
      WHERE h.mock_test_submission_id = s.submission_id AND h.skill_type = 'WRITING');
