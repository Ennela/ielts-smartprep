-- V56: An image for a question group -- the diagram, map or plan the questions label.
--
-- Diagram Label Completion in Reading and Map/Plan/Diagram Labelling in Listening (in most
-- Cambridge Part 2s) cannot be asked without the picture, and there was nowhere to keep
-- one, which is why the Cambridge 19 seeds have no map labelling at all.
--
-- Stored on the question like the other group fields: the first question of the group
-- that has one supplies it. The URL is a path on this site (/api/v1/images/...), uploaded
-- through the admin pages; the page's Content-Security-Policy allows images from this
-- origin only, so a picture linked from another site would not be shown.
ALTER TABLE reading_questions
    ADD COLUMN image_url VARCHAR(512) NULL;

ALTER TABLE listening_questions
    ADD COLUMN image_url VARCHAR(512) NULL;
