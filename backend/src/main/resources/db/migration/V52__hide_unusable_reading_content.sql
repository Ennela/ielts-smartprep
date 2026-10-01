-- V52: Stop serving Reading passages that nobody can read.
--
-- Two kinds of passage are served to learners today that cannot be answered:
--
--   * The V32 seed. Its twelve passages are titles only -- '[Cambridge 19 Test 1 Passage 1:
--     How tennis rackets have changed]' -- and V39 marked them PUBLISHED. The file says so
--     on its second line: "Passage texts are placeholders". Every fresh database gets them,
--     and V33 builds four mock tests out of them.
--   * An import that interleaved a hyphen with every character, so "In 2016, the" is stored
--     as "-I-n- -2-0-1-6-,- -t-h-e-". It exists in databases that ran that import.
--
-- A learner handed either one scores near zero through no fault of their own; a mock test
-- containing one produces a band that means nothing.
--
-- Hidden rather than repaired. The real text is Cambridge IELTS 19, which the project has
-- already decided not to distribute (FE-01 removed the answer keys and book scans), and
-- rebuilding it here would put it back.
--
-- Hidden rather than deleted. deleted_at is the one flag every learner-facing list honours;
-- content_status is not consulted by any of them, so setting it would change nothing.
-- Results already recorded keep working: they reach their quiz and mock test through JPA
-- relations, which do not apply the deleted_at filter.
--
-- The two signatures are narrow on purpose:
--   * a bare placeholder is the V32 prefix and under 200 characters; the shortest real
--     passage in a live database is over 3,000.
--   * the corruption is eight hyphen-letter pairs in a row -- a word spelled out one letter
--     at a time. Ordinary prose does not contain that: "well-known" has one such pair.

-- Mock tests first, while the passages they contain can still be matched.
UPDATE mock_tests m
SET m.deleted_at = NOW()
WHERE m.deleted_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM mock_test_reading_quizzes mr
      JOIN reading_quizzes q ON q.quiz_id = mr.quiz_id
      WHERE mr.mock_test_id = m.mock_test_id
        AND (
            (q.passage_text LIKE '[Cambridge 19 Test %' AND CHAR_LENGTH(q.passage_text) < 200)
            OR q.passage_text REGEXP '(-[[:alpha:]]){8}'
        )
  );

-- Then the passages. A submitted one is left alone: it is a learner's record rather than
-- content anybody is offered, and their result page still reads it.
UPDATE reading_quizzes
SET deleted_at = NOW()
WHERE deleted_at IS NULL
  AND submitted_at IS NULL
  AND (
      (passage_text LIKE '[Cambridge 19 Test %' AND CHAR_LENGTH(passage_text) < 200)
      OR passage_text REGEXP '(-[[:alpha:]]){8}'
  );
