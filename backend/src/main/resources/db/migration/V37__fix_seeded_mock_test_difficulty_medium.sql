-- V37: Fix seeded mock test difficulty values to match Difficulty enum constants (PASSAGE_1, PASSAGE_2, PASSAGE_3)
UPDATE mock_tests SET difficulty = 'PASSAGE_2' WHERE difficulty = 'MEDIUM';
UPDATE mock_tests SET difficulty = 'PASSAGE_1' WHERE difficulty = 'EASY';
UPDATE mock_tests SET difficulty = 'PASSAGE_3' WHERE difficulty = 'HARD';
