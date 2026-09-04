-- V46: Retire the legacy default administrator *identity* without deleting the account.
--
-- V11 created admin@smartprep.local with its password printed in the comment above the
-- hash. V40 disabled that credential by overwriting the hash with a sentinel, but left the
-- identity in place. That still left a hole: PasswordResetService looks users up by email,
-- so anyone who could receive mail at the published address could set a new password and
-- obtain an administrator account. Disabling a credential is not the same as retiring an
-- identity.
--
-- Why this renames rather than deletes. Almost every foreign key into users is
-- ON DELETE CASCADE, so removing the row would take everything the account owns with it:
-- vocabulary, reading_quizzes and the questions and answers beneath them, listening_tests,
-- score_history, writing_submissions, writing_full_submissions, mock_test_sessions and
-- mock_test_submissions. That account is not necessarily dormant -- on the database this
-- was written against it held 45 vocabulary items, 22 reading quizzes, 16 score-history
-- rows, 6 essays and 2 listening tests. Renaming closes the same hole and keeps the study
-- history intact.
--
-- The new address uses the .invalid top-level domain, which RFC 2606 reserves as
-- permanently unresolvable. No mail can ever be delivered to it, so the password-reset
-- route into this account is closed by construction rather than by hoping nobody registers
-- the domain.
--
-- The account keeps its role and its password. Log in with the username below rather than
-- the old one. To use your own identity instead, run:
--
--   UPDATE users SET email = 'you@example.com', username = 'your-name'
--   WHERE username = 'legacy-admin';

UPDATE users
SET email    = 'legacy-admin@invalid',
    username = 'legacy-admin'
WHERE email    = 'admin@smartprep.local'
  AND username = 'admin';
