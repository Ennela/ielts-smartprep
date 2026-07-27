-- Add content_status column to content entities
ALTER TABLE reading_quizzes ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE listening_parts ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE writing_prompts ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE mock_tests ADD COLUMN content_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';

-- Existing content is already in use → mark as PUBLISHED
UPDATE reading_quizzes SET content_status = 'PUBLISHED' WHERE content_status = 'DRAFT';
UPDATE listening_parts SET content_status = 'PUBLISHED' WHERE content_status = 'DRAFT';
UPDATE writing_prompts SET content_status = 'PUBLISHED' WHERE content_status = 'DRAFT';
UPDATE mock_tests SET content_status = 'PUBLISHED' WHERE content_status = 'DRAFT';
