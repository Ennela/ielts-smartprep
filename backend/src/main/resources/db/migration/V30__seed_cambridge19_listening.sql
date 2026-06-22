-- Cambridge IELTS 19: Listening Data (4 tests x 4 parts = 16 parts, 160 questions)
-- Audio files served from MinIO with key cam19_testX_partY.mp3

-- ========== TEST 1 ==========
INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(1, 'Hinchingbrooke Country Park', 'Education', '/api/v1/listening/audio/cam19_test1_part1.mp3', 'READY', NULL, 480, 'CAMBRIDGE_19');
SET @t1p1 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t1p1, 'FILL_BLANK', 'The park covers ___ hectares.', '69', 1),
(@t1p1, 'FILL_BLANK', 'The wetland area includes lakes, ponds and a ___.', 'stream', 2),
(@t1p1, 'FILL_BLANK', 'In the science session, children collect ___.', 'data', 3),
(@t1p1, 'FILL_BLANK', 'Students learn to read a ___ and use a compass.', 'map', 4),
(@t1p1, 'FILL_BLANK', 'The tourism section focuses on ___ to the park.', 'visitors', 5),
(@t1p1, 'FILL_BLANK', 'In the music session, children identify natural ___.', 'sounds', 6),
(@t1p1, 'FILL_BLANK', 'The visits give children a feeling of ___.', 'freedom', 7),
(@t1p1, 'FILL_BLANK', 'Children develop new practical ___.', 'skills', 8),
(@t1p1, 'FILL_BLANK', 'The cost per child is ___ pounds.', '4.95', 9),
(@t1p1, 'FILL_BLANK', 'Group ___ do not have to pay.', 'leaders', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(2, 'Stanthorpe Twinning Association', 'Culture', '/api/v1/listening/audio/cam19_test1_part2.mp3', 'READY', NULL, 450, 'CAMBRIDGE_19');
SET @t1p2 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t1p2, 'MCQ', 'Choose the correct answer (Q11).', 'B', 1),
(@t1p2, 'MCQ', 'Choose the correct answer (Q12).', 'A', 2),
(@t1p2, 'MCQ', 'Choose the correct answer (Q13).', 'B', 3),
(@t1p2, 'MCQ', 'Choose the correct answer (Q14).', 'C', 4),
(@t1p2, 'MCQ', 'Choose the correct answer (Q15).', 'A', 5),
(@t1p2, 'FILL_BLANK', 'Match item 16 to the correct letter.', 'G', 6),
(@t1p2, 'FILL_BLANK', 'Match item 17 to the correct letter.', 'C', 7),
(@t1p2, 'FILL_BLANK', 'Match item 18 to the correct letter.', 'B', 8),
(@t1p2, 'FILL_BLANK', 'Match item 19 to the correct letter.', 'D', 9),
(@t1p2, 'FILL_BLANK', 'Match item 20 to the correct letter.', 'A', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(3, 'Food Trends', 'Health', '/api/v1/listening/audio/cam19_test1_part3.mp3', 'READY', NULL, 430, 'CAMBRIDGE_19');
SET @t1p3 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t1p3, 'FILL_BLANK', 'Match item 21 to the correct letter.', 'B', 1),
(@t1p3, 'FILL_BLANK', 'Match item 22 to the correct letter.', 'D', 2),
(@t1p3, 'FILL_BLANK', 'Match item 23 to the correct letter.', 'A', 3),
(@t1p3, 'FILL_BLANK', 'Match item 24 to the correct letter.', 'E', 4),
(@t1p3, 'FILL_BLANK', 'Match item 25 to the correct letter.', 'D', 5),
(@t1p3, 'FILL_BLANK', 'Match item 26 to the correct letter.', 'G', 6),
(@t1p3, 'FILL_BLANK', 'Match item 27 to the correct letter.', 'C', 7),
(@t1p3, 'FILL_BLANK', 'Match item 28 to the correct letter.', 'B', 8),
(@t1p3, 'FILL_BLANK', 'Match item 29 to the correct letter.', 'F', 9),
(@t1p3, 'FILL_BLANK', 'Match item 30 to the correct letter.', 'H', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(4, 'Ceide Fields', 'History', '/api/v1/listening/audio/cam19_test1_part4.mp3', 'READY', NULL, 420, 'CAMBRIDGE_19');
SET @t1p4 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t1p4, 'FILL_BLANK', 'The ___ were used to divide the land.', 'walls', 1),
(@t1p4, 'FILL_BLANK', 'The discovery was first made by a local man''s ___.', 'son', 2),
(@t1p4, 'FILL_BLANK', 'The ___ was obtained from the bog.', 'fuel', 3),
(@t1p4, 'FILL_BLANK', 'The bog preserved items because it lacked ___.', 'oxygen', 4),
(@t1p4, 'FILL_BLANK', 'The shape of the fields was ___.', 'rectangular', 5),
(@t1p4, 'FILL_BLANK', 'Archaeologists used ___ to see underground.', 'lamps', 6),
(@t1p4, 'FILL_BLANK', 'Each field was worked by one ___.', 'family', 7),
(@t1p4, 'FILL_BLANK', 'The animals were kept indoors during ___.', 'winter', 8),
(@t1p4, 'FILL_BLANK', 'Changes in the ___ eventually made farming difficult.', 'soil', 9),
(@t1p4, 'FILL_BLANK', 'Increased ___ contributed to the decline.', 'rain', 10);

-- ========== TEST 2 ==========
INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(1, 'Music Classes', 'Education', '/api/v1/listening/audio/cam19_test2_part1.mp3', 'READY', NULL, 490, 'CAMBRIDGE_19');
SET @t2p1 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t2p1, 'FILL_BLANK', 'The teacher''s surname is ___.', 'Mathieson', 1),
(@t2p1, 'FILL_BLANK', 'The class is suitable for ___.', 'beginners', 2),
(@t2p1, 'FILL_BLANK', 'Classes take place at the ___.', 'college', 3),
(@t2p1, 'FILL_BLANK', 'The street name is ___ Street.', 'New', 4),
(@t2p1, 'FILL_BLANK', 'The class starts at ___ am.', '11', 5),
(@t2p1, 'FILL_BLANK', 'Students do not need to bring an ___.', 'instrument', 6),
(@t2p1, 'FILL_BLANK', 'Students learn to play by ___.', 'ear', 7),
(@t2p1, 'FILL_BLANK', 'One activity involves ___ along to the beat.', 'clapping', 8),
(@t2p1, 'FILL_BLANK', 'Students will make a ___ of their performance.', 'recording', 9),
(@t2p1, 'FILL_BLANK', 'Students also practise playing ___.', 'alone', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(2, 'Local Area Guide', 'Culture', '/api/v1/listening/audio/cam19_test2_part2.mp3', 'READY', NULL, 390, 'CAMBRIDGE_19');
SET @t2p2 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t2p2, 'MCQ', 'Choose the correct answer (Q11).', 'A', 1),
(@t2p2, 'MCQ', 'Choose the correct answer (Q12).', 'B', 2),
(@t2p2, 'MCQ', 'Choose the correct answer (Q13).', 'A', 3),
(@t2p2, 'MCQ', 'Choose the correct answer (Q14).', 'B', 4),
(@t2p2, 'MCQ', 'Choose the correct answer (Q15).', 'C', 5),
(@t2p2, 'FILL_BLANK', 'Match item 16 to the correct letter.', 'A', 6),
(@t2p2, 'FILL_BLANK', 'Match item 17 to the correct letter.', 'C', 7),
(@t2p2, 'FILL_BLANK', 'Match item 18 to the correct letter.', 'E', 8),
(@t2p2, 'FILL_BLANK', 'Match item 19 to the correct letter.', 'A', 9),
(@t2p2, 'FILL_BLANK', 'Match item 20 to the correct letter.', 'B', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(3, 'University Project', 'Academic', '/api/v1/listening/audio/cam19_test2_part3.mp3', 'READY', NULL, 420, 'CAMBRIDGE_19');
SET @t2p3 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t2p3, 'FILL_BLANK', 'Match item 21 to the correct letter.', 'A', 1),
(@t2p3, 'FILL_BLANK', 'Match item 22 to the correct letter.', 'B', 2),
(@t2p3, 'FILL_BLANK', 'Match item 23 to the correct letter.', 'B', 3),
(@t2p3, 'FILL_BLANK', 'Match item 24 to the correct letter.', 'B', 4),
(@t2p3, 'FILL_BLANK', 'Match item 25 to the correct letter.', 'E', 5),
(@t2p3, 'FILL_BLANK', 'Match item 26 to the correct letter.', 'B', 6),
(@t2p3, 'FILL_BLANK', 'Match item 27 to the correct letter.', 'A', 7),
(@t2p3, 'FILL_BLANK', 'Match item 28 to the correct letter.', 'C', 8),
(@t2p3, 'FILL_BLANK', 'Match item 29 to the correct letter.', 'C', 9),
(@t2p3, 'FILL_BLANK', 'Match item 30 to the correct letter.', 'A', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(4, 'Deep-Sea Fish', 'Science', '/api/v1/listening/audio/cam19_test2_part4.mp3', 'READY', NULL, 440, 'CAMBRIDGE_19');
SET @t2p4 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t2p4, 'FILL_BLANK', 'Deep-sea fish need to ___ constantly.', 'move', 1),
(@t2p4, 'FILL_BLANK', 'Their lifespan is relatively ___.', 'short', 2),
(@t2p4, 'FILL_BLANK', 'Their spine is made of ___.', 'discs', 3),
(@t2p4, 'FILL_BLANK', 'They can survive with very little ___.', 'oxygen', 4),
(@t2p4, 'FILL_BLANK', 'They breathe through a ___.', 'tube', 5),
(@t2p4, 'FILL_BLANK', 'They can withstand extreme ___.', 'temperatures', 6),
(@t2p4, 'FILL_BLANK', 'Their diet is rich in ___.', 'protein', 7),
(@t2p4, 'FILL_BLANK', 'They need a lot of ___ to swim.', 'space', 8),
(@t2p4, 'FILL_BLANK', 'Some species feed on ___.', 'seaweed', 9),
(@t2p4, 'FILL_BLANK', 'Several species are now ___.', 'endangered', 10);

-- ========== TEST 3 ==========
INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(1, 'Local Food Shops', 'Culture', '/api/v1/listening/audio/cam19_test3_part1.mp3', 'READY', NULL, 400, 'CAMBRIDGE_19');
SET @t3p1 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t3p1, 'FILL_BLANK', 'The shop is near the ___.', 'harbour', 1),
(@t3p1, 'FILL_BLANK', 'You cross a ___ to get there.', 'bridge', 2),
(@t3p1, 'FILL_BLANK', 'The shop closes at ___.', '3.30', 3),
(@t3p1, 'FILL_BLANK', 'The owner''s name is ___.', 'Rose', 4),
(@t3p1, 'FILL_BLANK', 'Look for the ___ outside.', 'sign', 5),
(@t3p1, 'FILL_BLANK', 'The building is painted ___.', 'purple', 6),
(@t3p1, 'FILL_BLANK', 'A local speciality is ___.', 'samphire', 7),
(@t3p1, 'FILL_BLANK', 'They also sell ___ from the region.', 'melon', 8),
(@t3p1, 'FILL_BLANK', 'An imported item available is ___.', 'coconut', 9),
(@t3p1, 'FILL_BLANK', 'The most popular fruit is ___.', 'strawberry', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(2, 'Volunteer Scheme', 'Community', '/api/v1/listening/audio/cam19_test3_part2.mp3', 'READY', NULL, 380, 'CAMBRIDGE_19');
SET @t3p2 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t3p2, 'MCQ', 'Choose the correct answer (Q11).', 'B', 1),
(@t3p2, 'MCQ', 'Choose the correct answer (Q12).', 'C', 2),
(@t3p2, 'MCQ', 'Choose the correct answer (Q13).', 'A', 3),
(@t3p2, 'MCQ', 'Choose the correct answer (Q14).', 'C', 4),
(@t3p2, 'MCQ', 'Choose the correct answer (Q15).', 'B', 5),
(@t3p2, 'FILL_BLANK', 'Match item 16.', 'E', 6),
(@t3p2, 'FILL_BLANK', 'Match item 17.', 'A', 7),
(@t3p2, 'FILL_BLANK', 'Match item 18.', 'G', 8),
(@t3p2, 'FILL_BLANK', 'Match item 19.', 'D', 9),
(@t3p2, 'FILL_BLANK', 'Match item 20.', 'F', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(3, 'Science Experiment', 'Science', '/api/v1/listening/audio/cam19_test3_part3.mp3', 'READY', NULL, 430, 'CAMBRIDGE_19');
SET @t3p3 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t3p3, 'FILL_BLANK', 'Match Q21.', 'C', 1),
(@t3p3, 'FILL_BLANK', 'Match Q22.', 'B', 2),
(@t3p3, 'FILL_BLANK', 'Match Q23.', 'A', 3),
(@t3p3, 'FILL_BLANK', 'Match Q24.', 'A', 4),
(@t3p3, 'FILL_BLANK', 'Match Q25.', 'C', 5),
(@t3p3, 'FILL_BLANK', 'Match Q26.', 'C', 6),
(@t3p3, 'FILL_BLANK', 'Match Q27.', 'H', 7),
(@t3p3, 'FILL_BLANK', 'Match Q28.', 'E', 8),
(@t3p3, 'FILL_BLANK', 'Match Q29.', 'B', 9),
(@t3p3, 'FILL_BLANK', 'Match Q30.', 'F', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(4, 'History of Glass', 'History', '/api/v1/listening/audio/cam19_test3_part4.mp3', 'READY', NULL, 430, 'CAMBRIDGE_19');
SET @t3p4 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t3p4, 'FILL_BLANK', 'Glass was first made in the form of ___.', 'beads', 1),
(@t3p4, 'FILL_BLANK', 'Early glass was coloured using ___.', 'metals', 2),
(@t3p4, 'FILL_BLANK', 'Glassblowing was invented in ___.', 'Syria', 3),
(@t3p4, 'FILL_BLANK', 'Roman glass was used for ___.', 'windows', 4),
(@t3p4, 'FILL_BLANK', 'In the Middle Ages glass was used in ___.', 'churches', 5),
(@t3p4, 'FILL_BLANK', 'Venetian glassmakers worked on the island of ___.', 'Murano', 6),
(@t3p4, 'FILL_BLANK', 'They developed a type of clear ___.', 'crystal', 7),
(@t3p4, 'FILL_BLANK', 'Industrial glass was produced using ___.', 'machines', 8),
(@t3p4, 'FILL_BLANK', 'Modern glass can be made ___.', 'stronger', 9),
(@t3p4, 'FILL_BLANK', 'Glass is now used in ___ technology.', 'fibre', 10);

-- ========== TEST 4 ==========
INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(1, 'First Day at Work', 'Employment', '/api/v1/listening/audio/cam19_test4_part1.mp3', 'READY', NULL, 410, 'CAMBRIDGE_19');
SET @t4p1 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t4p1, 'FILL_BLANK', 'The supervisor''s name is ___.', 'Rawlings', 1),
(@t4p1, 'FILL_BLANK', 'Staff should keep belongings in ___.', 'lockers', 2),
(@t4p1, 'FILL_BLANK', 'Contact HR on extension ___.', '378', 3),
(@t4p1, 'FILL_BLANK', 'You need to collect your ___.', 'badge', 4),
(@t4p1, 'FILL_BLANK', 'The canteen is on the ___ floor.', 'third', 5),
(@t4p1, 'FILL_BLANK', 'Call the ___ number if you are late.', 'mobile', 6),
(@t4p1, 'FILL_BLANK', 'Check the ___ for your daily tasks.', 'noticeboard', 7),
(@t4p1, 'FILL_BLANK', 'Wear the correct ___ at all times.', 'uniform', 8),
(@t4p1, 'FILL_BLANK', 'The morning break is ___ minutes.', '15', 9),
(@t4p1, 'FILL_BLANK', 'Report any problems to your ___.', 'manager', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(2, 'City Park Improvements', 'Environment', '/api/v1/listening/audio/cam19_test4_part2.mp3', 'READY', NULL, 400, 'CAMBRIDGE_19');
SET @t4p2 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t4p2, 'MCQ', 'Choose the correct answer (Q11).', 'C', 1),
(@t4p2, 'MCQ', 'Choose the correct answer (Q12).', 'A', 2),
(@t4p2, 'MCQ', 'Choose the correct answer (Q13).', 'B', 3),
(@t4p2, 'MCQ', 'Choose the correct answer (Q14).', 'A', 4),
(@t4p2, 'MCQ', 'Choose the correct answer (Q15).', 'C', 5),
(@t4p2, 'FILL_BLANK', 'Match item 16.', 'D', 6),
(@t4p2, 'FILL_BLANK', 'Match item 17.', 'G', 7),
(@t4p2, 'FILL_BLANK', 'Match item 18.', 'A', 8),
(@t4p2, 'FILL_BLANK', 'Match item 19.', 'F', 9),
(@t4p2, 'FILL_BLANK', 'Match item 20.', 'B', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(3, 'Types of Books', 'Education', '/api/v1/listening/audio/cam19_test4_part3.mp3', 'READY', NULL, 430, 'CAMBRIDGE_19');
SET @t4p3 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t4p3, 'FILL_BLANK', 'Match Q21.', 'A', 1),
(@t4p3, 'FILL_BLANK', 'Match Q22.', 'C', 2),
(@t4p3, 'FILL_BLANK', 'Match Q23.', 'A', 3),
(@t4p3, 'FILL_BLANK', 'Match Q24.', 'B', 4),
(@t4p3, 'FILL_BLANK', 'Match Q25.', 'C', 5),
(@t4p3, 'FILL_BLANK', 'Match Q26.', 'D', 6),
(@t4p3, 'FILL_BLANK', 'Match Q27.', 'F', 7),
(@t4p3, 'FILL_BLANK', 'Match Q28.', 'A', 8),
(@t4p3, 'FILL_BLANK', 'Match Q29.', 'C', 9),
(@t4p3, 'FILL_BLANK', 'Match Q30.', 'G', 10);

INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, duration_seconds, created_by) VALUES
(4, 'Marine Archaeology', 'History', '/api/v1/listening/audio/cam19_test4_part4.mp3', 'READY', NULL, 530, 'CAMBRIDGE_19');
SET @t4p4 = LAST_INSERT_ID();
INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index) VALUES
(@t4p4, 'FILL_BLANK', 'The ship carried ___.', 'grain', 1),
(@t4p4, 'FILL_BLANK', 'Divers found ___ on the seabed.', 'coins', 2),
(@t4p4, 'FILL_BLANK', 'The wreck was dated using ___.', 'pottery', 3),
(@t4p4, 'FILL_BLANK', 'The hull was made of ___.', 'pine', 4),
(@t4p4, 'FILL_BLANK', 'The ship''s cargo included ___.', 'oil', 5),
(@t4p4, 'FILL_BLANK', 'The crew probably numbered ___.', 'twelve', 6),
(@t4p4, 'FILL_BLANK', 'The anchor was made of ___.', 'stone', 7),
(@t4p4, 'FILL_BLANK', 'The site is now a ___.', 'museum', 8),
(@t4p4, 'FILL_BLANK', 'Visitors can see a ___.', 'replica', 9),
(@t4p4, 'FILL_BLANK', 'The project received government ___.', 'funding', 10);
