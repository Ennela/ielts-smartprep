ALTER TABLE listening_parts
    ADD COLUMN audio_status VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        AFTER audio_url;

-- Mark all existing seeded parts as READY (they have placeholder audio but are usable)
UPDATE listening_parts SET audio_status = 'READY' WHERE part_id > 0;
