-- V60: A Writing prompt can carry the topic it was written for.
--
-- The question bank serves stored prompts before Gemini is called, and a learner who picks a
-- topic should get a prompt on it. Prompts imported from NotebookLM and those Gemini writes
-- from now on record it; the earlier prompts keep NULL and are offered only when the learner
-- asks for any topic.
ALTER TABLE writing_prompts
    ADD COLUMN topic VARCHAR(20) NULL;
