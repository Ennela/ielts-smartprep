-- V45: Store every enum-backed column as VARCHAR.
--
-- The schema had drifted into two conventions: V32 converted a group of columns to native
-- MySQL ENUM, while V39/V42/V43 added their enum columns as VARCHAR. That left 16 ENUM and
-- 13 VARCHAR columns holding the same kind of value. It went unnoticed because ddl-auto was
-- `none` in every profile that ran, so nothing ever validated entities against the schema.
-- Enabling the prod profile turns on ddl-auto=validate, which fails on the mismatch:
-- Hibernate 6 maps @Enumerated(STRING) to VARCHAR, not to a native ENUM.
--
-- VARCHAR is the target rather than ENUM because adding a value to a Java enum then needs no
-- ALTER TABLE, and the schema stays portable. ENUM and VARCHAR both store the label as text,
-- so no data is rewritten or lost by this conversion.
--
-- Lengths match each entity's @Column(length=...) with one exception: listening_questions
-- .question_type is widened to 30 rather than the declared 15, because QuestionType contains
-- values up to 25 characters (MATCHING_SENTENCE_ENDINGS). Narrowing it to 15 would truncate.
-- ListeningQuestion.questionType is corrected to length = 30 in the same change.

ALTER TABLE exam_attempts          MODIFY COLUMN skill_type      VARCHAR(15) NOT NULL;
ALTER TABLE exam_attempts          MODIFY COLUMN status          VARCHAR(15) NOT NULL;
ALTER TABLE listening_parts        MODIFY COLUMN audio_status    VARCHAR(10) NOT NULL;
ALTER TABLE listening_questions    MODIFY COLUMN question_type   VARCHAR(30) NOT NULL;
ALTER TABLE listening_tests        MODIFY COLUMN test_mode       VARCHAR(15) NOT NULL;
ALTER TABLE mock_test_sections     MODIFY COLUMN section_type    VARCHAR(20) NOT NULL;
ALTER TABLE mock_test_sessions     MODIFY COLUMN current_section VARCHAR(20) NOT NULL;
ALTER TABLE mock_test_sessions     MODIFY COLUMN status          VARCHAR(20) NOT NULL;
ALTER TABLE mock_test_submissions  MODIFY COLUMN status          VARCHAR(20) NOT NULL;
ALTER TABLE reading_questions      MODIFY COLUMN question_type   VARCHAR(30) NOT NULL;
ALTER TABLE reading_quizzes        MODIFY COLUMN difficulty      VARCHAR(20) NOT NULL;
ALTER TABLE reading_quizzes        MODIFY COLUMN topic           VARCHAR(20) NOT NULL;
ALTER TABLE score_history          MODIFY COLUMN skill_type      VARCHAR(15) NOT NULL;
ALTER TABLE users                  MODIFY COLUMN role            VARCHAR(20) NOT NULL;
ALTER TABLE vocabulary             MODIFY COLUMN source_skill    VARCHAR(50) NULL;
ALTER TABLE writing_prompts        MODIFY COLUMN essay_type      VARCHAR(50) NOT NULL;
