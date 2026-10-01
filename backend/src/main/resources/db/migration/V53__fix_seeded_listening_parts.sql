-- V53: Stop serving seeded Listening parts that cannot be listened to.
--
-- A fresh database gets two seeds that look usable and are not:
--
--   * The V30 seed (Cambridge IELTS 19, 16 parts). Its audio is a MinIO key nobody uploads
--     on a new deployment, its transcript is NULL, and 80 of its 160 questions -- all of
--     Parts 2 and 3 -- are placeholders like 'Choose the correct answer (Q11).' with no
--     options, which render with nothing to click or type into.
--   * The V6 seed (8 parts). These are fine -- real transcripts, real questions -- but
--     their audio_url names files that were never shipped, and V18 marked them READY. A
--     READY part is never voiced again, so learners got silence.
--
-- The running database was put right by hand: the V30 parts are gone and the V6 parts were
-- voiced again from their transcripts. This does the same on a database built from these
-- migrations. On the running database it matches nothing.
--
-- V30 parts are hidden, not repaired, for the reason V52 gives for the Reading seed: the
-- real content is Cambridge IELTS 19, which the project has decided not to distribute.
-- Hidden rather than deleted, because deleted_at is what every learner-facing list honours.
--
-- V6 parts go back to PENDING. ListeningAudioService voices PENDING parts that have a
-- transcript when the application starts, which is what a READY part never got.

-- Mock tests first, while the parts they contain can still be matched. V52 already hid the
-- four V33 mock tests built on this seed; this catches any other.
UPDATE mock_tests m
SET m.deleted_at = NOW()
WHERE m.deleted_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM mock_test_listening_parts ml
      JOIN listening_parts p ON p.part_id = ml.part_id
      WHERE ml.mock_test_id = m.mock_test_id
        AND p.created_by = 'CAMBRIDGE_19'
        AND p.transcript_text IS NULL
  );

-- A V30 part that someone has since given a transcript was repaired by hand; leave it.
UPDATE listening_parts
SET deleted_at = NOW()
WHERE deleted_at IS NULL
  AND created_by = 'CAMBRIDGE_19'
  AND transcript_text IS NULL;

-- Exactly the eight file names V6 wrote. Audio that was really generated is stored under
-- part_<id>_<timestamp>.mp3, so a part voiced since keeps its audio.
UPDATE listening_parts
SET audio_status = 'PENDING'
WHERE deleted_at IS NULL
  AND audio_status = 'READY'
  AND audio_url IN (
      '/api/v1/listening/audio/part1a.mp3', '/api/v1/listening/audio/part1b.mp3',
      '/api/v1/listening/audio/part2a.mp3', '/api/v1/listening/audio/part2b.mp3',
      '/api/v1/listening/audio/part3a.mp3', '/api/v1/listening/audio/part3b.mp3',
      '/api/v1/listening/audio/part4a.mp3', '/api/v1/listening/audio/part4b.mp3'
  );
