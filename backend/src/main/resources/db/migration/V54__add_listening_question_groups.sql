-- V54: Give Listening questions the group structure Reading questions have had since V8.
--
-- Most of a Cambridge Listening paper is grouped: a form or set of notes with numbered gaps
-- (Questions 1-10), a box of options A-H that five questions choose from (Questions 16-20).
-- listening_questions had no way to say which questions belong together or what they share,
-- which is why V30 had to store a matching question as a FILL_BLANK reading 'Match item 16
-- to the correct letter.' with nothing to match against.
--
-- Same columns, types and meaning as on reading_questions (V8, V15), so both skills can be
-- rendered and marked by the same code:
--   group_id       questions sharing it form one group
--   group_label    "Questions 11-15: Choose the correct letter, A, B or C."
--   group_context  shared text: the form or notes, with gaps written ___N___
--   options_json   options the whole group chooses from, a JSON array: ["A. ...", "B. ..."]
--   word_limit     "NO MORE THAN N WORDS"; an answer over it is wrong
--   explanation    shown when the test is reviewed
--
-- All nullable. Existing questions have no group and keep rendering as they do.
ALTER TABLE listening_questions
    ADD COLUMN group_id INT NULL,
    ADD COLUMN group_label VARCHAR(255) NULL,
    ADD COLUMN group_context TEXT NULL,
    ADD COLUMN options_json TEXT NULL,
    ADD COLUMN word_limit INT NULL,
    ADD COLUMN explanation TEXT NULL;
