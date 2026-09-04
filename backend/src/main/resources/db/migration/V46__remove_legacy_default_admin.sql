-- V46: Remove the legacy default administrator seeded by V11.
--
-- V11 created admin@smartprep.local with its password written in the comment above the
-- hash. V40 disabled that credential by overwriting the hash with a sentinel, but left the
-- row in place, still carrying role = 'ADMIN'. Two things follow. The published identity
-- remains a live target for the password-reset flow, so whoever controls mail to that
-- address can set a new password and get an administrator account. And an ADMIN account
-- exists that no person is accountable for. Disabling a credential is not the same as
-- removing an identity; this removes it.
--
-- ---------------------------------------------------------------------------------------
-- READ THIS BEFORE APPLYING TO A DATABASE THAT MATTERS
--
-- Almost every foreign key pointing at users is ON DELETE CASCADE, so removing this row
-- also removes everything the account owns: vocabulary, reading_quizzes (and the questions
-- and answers beneath them), listening_tests, score_history, writing_submissions,
-- writing_full_submissions, mock_test_sessions and mock_test_submissions.
--
-- That is only harmless if the account was left dormant. It is not always dormant: on the
-- machine this migration was written for, it held 45 vocabulary items, 22 reading quizzes,
-- 16 score history rows, 6 writing submissions and 2 listening tests. Someone had been
-- studying with it. Check before you run this:
--
--   SELECT u.user_id,
--          (SELECT COUNT(*) FROM vocabulary          v WHERE v.user_id = u.user_id) AS vocab,
--          (SELECT COUNT(*) FROM reading_quizzes     r WHERE r.user_id = u.user_id) AS quizzes,
--          (SELECT COUNT(*) FROM score_history       s WHERE s.user_id = u.user_id) AS scores,
--          (SELECT COUNT(*) FROM writing_submissions w WHERE w.user_id = u.user_id) AS essays
--   FROM users u
--   WHERE u.email = 'admin@smartprep.local' AND u.username = 'admin';
--
-- If those counts are not zero and you want to keep the data, do not apply this as written.
-- Rename the identity instead, which closes the same hole without deleting anything:
--
--   UPDATE users SET email = 'you@example.com', username = 'your-name'
--   WHERE email = 'admin@smartprep.local' AND username = 'admin';
-- ---------------------------------------------------------------------------------------
--
-- exam_attempts is the one foreign key that is NO ACTION rather than CASCADE, so its rows
-- have to be removed explicitly. Left in place they would abort the DELETE below on a
-- constraint violation, and Flyway would stop the application from starting.

DELETE FROM exam_attempts
WHERE user_id IN (
    SELECT user_id FROM users
    WHERE email = 'admin@smartprep.local' AND username = 'admin'
);

DELETE FROM users
WHERE email = 'admin@smartprep.local'
  AND username = 'admin';
