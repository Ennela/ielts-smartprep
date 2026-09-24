-- V49: Context-aware vocabulary explanations.
--
-- The AI explanation is a nested document (senses, synonym comparisons, collocations,
-- grammar patterns, mistakes, IELTS guidance) that is only ever read as a whole for one
-- word. Storing it as one validated JSON payload keeps it in the row it belongs to
-- without six join tables that nothing would ever query independently.
--
-- Both columns are nullable: every existing row keeps working and simply has no
-- explanation until one is generated on demand.

ALTER TABLE vocabulary
    ADD COLUMN insight_json LONGTEXT NULL,
    ADD COLUMN insight_generated_at TIMESTAMP NULL;
