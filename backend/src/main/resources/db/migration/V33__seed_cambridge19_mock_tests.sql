-- Cambridge IELTS 19: Mock Tests (4 tests linking listening, reading, writing)
-- Uses subqueries to find the correct IDs dynamically

-- Create 4 mock tests
INSERT INTO mock_tests (title, description, difficulty) VALUES
('Cambridge IELTS 19 Test 1', 'Full academic practice test from Cambridge IELTS 19 Book. Includes Listening (Parts 1-4), Reading (Passages 1-3), and Writing (Tasks 1 & 2).', 'PASSAGE_2');
SET @mt1 = LAST_INSERT_ID();

INSERT INTO mock_tests (title, description, difficulty) VALUES
('Cambridge IELTS 19 Test 2', 'Full academic practice test from Cambridge IELTS 19 Book. Includes Listening (Parts 1-4), Reading (Passages 1-3), and Writing (Tasks 1 & 2).', 'PASSAGE_2');
SET @mt2 = LAST_INSERT_ID();

INSERT INTO mock_tests (title, description, difficulty) VALUES
('Cambridge IELTS 19 Test 3', 'Full academic practice test from Cambridge IELTS 19 Book. Includes Listening (Parts 1-4), Reading (Passages 1-3), and Writing (Tasks 1 & 2).', 'PASSAGE_2');
SET @mt3 = LAST_INSERT_ID();

INSERT INTO mock_tests (title, description, difficulty) VALUES
('Cambridge IELTS 19 Test 4', 'Full academic practice test from Cambridge IELTS 19 Book. Includes Listening (Parts 1-4), Reading (Passages 1-3), and Writing (Tasks 1 & 2).', 'PASSAGE_2');
SET @mt4 = LAST_INSERT_ID();

-- Link listening parts (find by title + created_by)
SET @lp_t1p1 = (SELECT part_id FROM listening_parts WHERE title='Hinchingbrooke Country Park' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t1p2 = (SELECT part_id FROM listening_parts WHERE title='Stanthorpe Twinning Association' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t1p3 = (SELECT part_id FROM listening_parts WHERE title='Food Trends' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t1p4 = (SELECT part_id FROM listening_parts WHERE title='Ceide Fields' AND created_by='CAMBRIDGE_19' LIMIT 1);

INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES
(@mt1, @lp_t1p1, 0), (@mt1, @lp_t1p2, 1), (@mt1, @lp_t1p3, 2), (@mt1, @lp_t1p4, 3);

SET @lp_t2p1 = (SELECT part_id FROM listening_parts WHERE title='Music Classes' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t2p2 = (SELECT part_id FROM listening_parts WHERE title='Local Area Guide' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t2p3 = (SELECT part_id FROM listening_parts WHERE title='University Project' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t2p4 = (SELECT part_id FROM listening_parts WHERE title='Deep-Sea Fish' AND created_by='CAMBRIDGE_19' LIMIT 1);

INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES
(@mt2, @lp_t2p1, 0), (@mt2, @lp_t2p2, 1), (@mt2, @lp_t2p3, 2), (@mt2, @lp_t2p4, 3);

SET @lp_t3p1 = (SELECT part_id FROM listening_parts WHERE title='Local Food Shops' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t3p2 = (SELECT part_id FROM listening_parts WHERE title='Volunteer Scheme' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t3p3 = (SELECT part_id FROM listening_parts WHERE title='Science Experiment' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t3p4 = (SELECT part_id FROM listening_parts WHERE title='History of Glass' AND created_by='CAMBRIDGE_19' LIMIT 1);

INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES
(@mt3, @lp_t3p1, 0), (@mt3, @lp_t3p2, 1), (@mt3, @lp_t3p3, 2), (@mt3, @lp_t3p4, 3);

SET @lp_t4p1 = (SELECT part_id FROM listening_parts WHERE title='First Day at Work' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t4p2 = (SELECT part_id FROM listening_parts WHERE title='City Park Improvements' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t4p3 = (SELECT part_id FROM listening_parts WHERE title='Types of Books' AND created_by='CAMBRIDGE_19' LIMIT 1);
SET @lp_t4p4 = (SELECT part_id FROM listening_parts WHERE title='Marine Archaeology' AND created_by='CAMBRIDGE_19' LIMIT 1);

INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES
(@mt4, @lp_t4p1, 0), (@mt4, @lp_t4p2, 1), (@mt4, @lp_t4p3, 2), (@mt4, @lp_t4p4, 3);

-- Link reading quizzes (find by passage_text LIKE pattern)
SET @rq_t1p1 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 1 Passage 1%' AND is_template=TRUE LIMIT 1);
SET @rq_t1p2 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 1 Passage 2%' AND is_template=TRUE LIMIT 1);
SET @rq_t1p3 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 1 Passage 3%' AND is_template=TRUE LIMIT 1);

INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order) VALUES
(@mt1, @rq_t1p1, 0), (@mt1, @rq_t1p2, 1), (@mt1, @rq_t1p3, 2);

SET @rq_t2p1 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 2 Passage 1%' AND is_template=TRUE LIMIT 1);
SET @rq_t2p2 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 2 Passage 2%' AND is_template=TRUE LIMIT 1);
SET @rq_t2p3 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 2 Passage 3%' AND is_template=TRUE LIMIT 1);

INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order) VALUES
(@mt2, @rq_t2p1, 0), (@mt2, @rq_t2p2, 1), (@mt2, @rq_t2p3, 2);

SET @rq_t3p1 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 3 Passage 1%' AND is_template=TRUE LIMIT 1);
SET @rq_t3p2 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 3 Passage 2%' AND is_template=TRUE LIMIT 1);
SET @rq_t3p3 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 3 Passage 3%' AND is_template=TRUE LIMIT 1);

INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order) VALUES
(@mt3, @rq_t3p1, 0), (@mt3, @rq_t3p2, 1), (@mt3, @rq_t3p3, 2);

SET @rq_t4p1 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 4 Passage 1%' AND is_template=TRUE LIMIT 1);
SET @rq_t4p2 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 4 Passage 2%' AND is_template=TRUE LIMIT 1);
SET @rq_t4p3 = (SELECT quiz_id FROM reading_quizzes WHERE passage_text LIKE '%Test 4 Passage 3%' AND is_template=TRUE LIMIT 1);

INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order) VALUES
(@mt4, @rq_t4p1, 0), (@mt4, @rq_t4p2, 1), (@mt4, @rq_t4p3, 2);

-- Link writing prompts (find by prompt_text patterns unique to Cam19)
SET @wp_t1_task1 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%social centre in Melbourne%' LIMIT 1);
SET @wp_t1_task2 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%competition at work, at school%' LIMIT 1);
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (@mt1, @wp_t1_task1, 0), (@mt1, @wp_t1_task2, 1);

SET @wp_t2_task1 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%harbour in 2000%' LIMIT 1);
SET @wp_t2_task2 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%working week should be shorter%' LIMIT 1);
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (@mt2, @wp_t2_task1, 0), (@mt2, @wp_t2_task2, 1);

SET @wp_t3_task1 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%biofuel called ethanol%' LIMIT 1);
SET @wp_t3_task2 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%save money for their future%' LIMIT 1);
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (@mt3, @wp_t3_task1, 0), (@mt3, @wp_t3_task2, 1);

SET @wp_t4_task1 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%dance classes young people%' LIMIT 1);
SET @wp_t4_task2 = (SELECT prompt_id FROM writing_prompts WHERE prompt_text LIKE '%buy food produced all over the world%' LIMIT 1);
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (@mt4, @wp_t4_task1, 0), (@mt4, @wp_t4_task2, 1);
