-- V42: Convert mock_tests.difficulty from PASSAGE_1/2/3 to EASY/MEDIUM/HARD
-- Backfill existing data (includes soft-deleted record mock_test_id=11)
UPDATE mock_tests SET difficulty = 'EASY'   WHERE difficulty = 'PASSAGE_1';
UPDATE mock_tests SET difficulty = 'MEDIUM' WHERE difficulty = 'PASSAGE_2';
UPDATE mock_tests SET difficulty = 'HARD'   WHERE difficulty = 'PASSAGE_3';
