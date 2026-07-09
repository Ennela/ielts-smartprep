-- V38: Add metadata fields to support import tracking and audit trail
ALTER TABLE reading_quizzes ADD COLUMN source VARCHAR(100) NULL;
ALTER TABLE reading_quizzes ADD COLUMN created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE reading_quizzes ADD COLUMN imported_at DATETIME NULL;

ALTER TABLE listening_parts ADD COLUMN source VARCHAR(100) NULL;
ALTER TABLE listening_parts ADD COLUMN imported_at DATETIME NULL;
-- Note: listening_parts already has created_by column from V19

ALTER TABLE writing_prompts ADD COLUMN source VARCHAR(100) NULL;
ALTER TABLE writing_prompts ADD COLUMN created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE writing_prompts ADD COLUMN imported_at DATETIME NULL;

ALTER TABLE mock_tests ADD COLUMN source VARCHAR(100) NULL;
ALTER TABLE mock_tests ADD COLUMN created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE mock_tests ADD COLUMN imported_at DATETIME NULL;

-- Backfill existing Cambridge IELTS 19 seed data that was added before these audit columns existed.
UPDATE listening_parts
SET source = 'Cambridge IELTS 19',
    imported_at = COALESCE(created_at, NOW())
WHERE created_by = 'CAMBRIDGE_19'
  AND source IS NULL;

UPDATE reading_quizzes
SET source = 'Cambridge IELTS 19',
    created_by = 'CAMBRIDGE_19',
    imported_at = COALESCE(created_at, NOW())
WHERE passage_text LIKE '[Cambridge 19 Test %'
  AND source IS NULL;

UPDATE writing_prompts
SET source = 'Cambridge IELTS 19',
    created_by = 'CAMBRIDGE_19',
    imported_at = COALESCE(created_at, NOW())
WHERE source IS NULL
  AND (
      prompt_text LIKE '%social centre in Melbourne%'
      OR prompt_text LIKE '%competition at work, at school%'
      OR prompt_text LIKE '%harbour in 2000%'
      OR prompt_text LIKE '%working week should be shorter%'
      OR prompt_text LIKE '%biofuel called ethanol%'
      OR prompt_text LIKE '%save money for their future%'
      OR prompt_text LIKE '%dance classes young people%'
      OR prompt_text LIKE '%buy food produced all over the world%'
  );

UPDATE mock_tests
SET source = title,
    created_by = 'CAMBRIDGE_19',
    imported_at = COALESCE(created_at, NOW())
WHERE title LIKE 'Cambridge IELTS 19 Test %'
  AND source IS NULL;
