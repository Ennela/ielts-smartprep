-- Cambridge IELTS 19: Reading Templates (12 passages) + Questions
-- Passage texts are placeholders - update with actual content from the book

-- Alter topic column to support SCIENCE and SOCIETY
ALTER TABLE reading_quizzes MODIFY COLUMN topic ENUM('ENVIRONMENT','TECHNOLOGY','HISTORY','HEALTH','EDUCATION','SCIENCE','SOCIETY') NOT NULL;

-- Alter question_type column to support all QuestionType enum values
ALTER TABLE reading_questions MODIFY COLUMN question_type ENUM('MCQ','TFNG','FILL_BLANK','YNNG','SENTENCE_COMPLETION','SUMMARY_COMPLETION','MATCHING_HEADINGS','MATCHING_INFORMATION','MATCHING_FEATURES','MATCHING_SENTENCE_ENDINGS','DIAGRAM_LABEL_COMPLETION','SHORT_ANSWER') NOT NULL;
ALTER TABLE listening_questions MODIFY COLUMN question_type ENUM('MCQ','TFNG','FILL_BLANK','YNNG','SENTENCE_COMPLETION','SUMMARY_COMPLETION','MATCHING_HEADINGS','MATCHING_INFORMATION','MATCHING_FEATURES','MATCHING_SENTENCE_ENDINGS','DIAGRAM_LABEL_COMPLETION','SHORT_ANSWER') NOT NULL;

-- ===== TEST 1 =====
INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'TECHNOLOGY', 'PASSAGE_1', '[Cambridge 19 Test 1 Passage 1: How tennis rackets have changed]', 13, TRUE, 'ACADEMIC');
SET @r1p1 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r1p1,'TFNG','Statement about racket development (Q1)','FALSE',1,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q2)','FALSE',2,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q3)','NOT GIVEN',3,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q4)','FALSE',4,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q5)','NOT GIVEN',5,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q6)','TRUE',6,'Questions 1-7: TFNG',1),
(@r1p1,'TFNG','Statement about racket development (Q7)','TRUE',7,'Questions 1-7: TFNG',1),
(@r1p1,'FILL_BLANK','Note completion (Q8)','paint',8,'Questions 8-13: Note Completion',2),
(@r1p1,'FILL_BLANK','Note completion (Q9)','topspin',9,'Questions 8-13: Note Completion',2),
(@r1p1,'FILL_BLANK','Note completion (Q10)','training',10,'Questions 8-13: Note Completion',2),
(@r1p1,'FILL_BLANK','Note completion (Q11)','intestines',11,'Questions 8-13: Note Completion',2),
(@r1p1,'FILL_BLANK','Note completion (Q12)','weights',12,'Questions 8-13: Note Completion',2),
(@r1p1,'FILL_BLANK','Note completion (Q13)','grips',13,'Questions 8-13: Note Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'SOCIETY', 'PASSAGE_2', '[Cambridge 19 Test 1 Passage 2: The history of money]', 13, TRUE, 'ACADEMIC');
SET @r1p2 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q14)','D',1,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q15)','G',2,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q16)','C',3,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q17)','A',4,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q18)','G',5,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MATCHING_INFORMATION','Match to paragraph (Q19)','B',6,'Questions 14-19: Match paragraphs',1),
(@r1p2,'MCQ','Multiple choice (Q20)','B',7,'Questions 20-23: MCQ',2),
(@r1p2,'MCQ','Multiple choice (Q21)','D',8,'Questions 20-23: MCQ',2),
(@r1p2,'MCQ','Multiple choice (Q22)','C',9,'Questions 20-23: MCQ',2),
(@r1p2,'MCQ','Multiple choice (Q23)','E',10,'Questions 20-23: MCQ',2),
(@r1p2,'SENTENCE_COMPLETION','Complete the sentence (Q24)','grain',11,'Questions 24-26: Sentence completion',3),
(@r1p2,'SENTENCE_COMPLETION','Complete the sentence (Q25)','punishment',12,'Questions 24-26: Sentence completion',3),
(@r1p2,'SENTENCE_COMPLETION','Complete the sentence (Q26)','ransom',13,'Questions 24-26: Sentence completion',3);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'ENVIRONMENT', 'PASSAGE_3', '[Cambridge 19 Test 1 Passage 3: Environmental psychology]', 14, TRUE, 'ACADEMIC');
SET @r1p3 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r1p3,'MCQ','Multiple choice (Q27)','D',1,'Questions 27-30: MCQ',1),
(@r1p3,'MCQ','Multiple choice (Q28)','A',2,'Questions 27-30: MCQ',1),
(@r1p3,'MCQ','Multiple choice (Q29)','C',3,'Questions 27-30: MCQ',1),
(@r1p3,'MCQ','Multiple choice (Q30)','D',4,'Questions 27-30: MCQ',1),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q31)','G',5,'Questions 31-36: Matching',2),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q32)','J',6,'Questions 31-36: Matching',2),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q33)','H',7,'Questions 31-36: Matching',2),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q34)','B',8,'Questions 31-36: Matching',2),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q35)','E',9,'Questions 31-36: Matching',2),
(@r1p3,'MATCHING_INFORMATION','Match to section (Q36)','C',10,'Questions 31-36: Matching',2),
(@r1p3,'YNNG','Statement (Q37)','YES',11,'Questions 37-40: YNNG',3),
(@r1p3,'YNNG','Statement (Q38)','NOT GIVEN',12,'Questions 37-40: YNNG',3),
(@r1p3,'YNNG','Statement (Q39)','NO',13,'Questions 37-40: YNNG',3),
(@r1p3,'YNNG','Statement (Q40)','NOT GIVEN',14,'Questions 37-40: YNNG',3);

-- ===== TEST 2 =====
INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'ENVIRONMENT', 'PASSAGE_1', '[Cambridge 19 Test 2 Passage 1: Urban farming]', 13, TRUE, 'ACADEMIC');
SET @r2p1 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r2p1,'TFNG','Statement (Q1)','TRUE',1,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q2)','FALSE',2,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q3)','NOT GIVEN',3,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q4)','TRUE',4,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q5)','FALSE',5,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q6)','NOT GIVEN',6,'Questions 1-7: TFNG',1),
(@r2p1,'TFNG','Statement (Q7)','TRUE',7,'Questions 1-7: TFNG',1),
(@r2p1,'FILL_BLANK','Completion (Q8)','__(answer)',8,'Questions 8-13: Completion',2),
(@r2p1,'FILL_BLANK','Completion (Q9)','__(answer)',9,'Questions 8-13: Completion',2),
(@r2p1,'FILL_BLANK','Completion (Q10)','__(answer)',10,'Questions 8-13: Completion',2),
(@r2p1,'FILL_BLANK','Completion (Q11)','__(answer)',11,'Questions 8-13: Completion',2),
(@r2p1,'FILL_BLANK','Completion (Q12)','__(answer)',12,'Questions 8-13: Completion',2),
(@r2p1,'FILL_BLANK','Completion (Q13)','__(answer)',13,'Questions 8-13: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'SCIENCE', 'PASSAGE_2', '[Cambridge 19 Test 2 Passage 2: History of weather forecasting]', 13, TRUE, 'ACADEMIC');
SET @r2p2 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r2p2,'MATCHING_HEADINGS','Match heading (Q14)','vi',1,'Questions 14-19: Matching headings',1),
(@r2p2,'MATCHING_HEADINGS','Match heading (Q15)','ii',2,'Questions 14-19: Matching headings',1),
(@r2p2,'MATCHING_HEADINGS','Match heading (Q16)','vii',3,'Questions 14-19: Matching headings',1),
(@r2p2,'MATCHING_HEADINGS','Match heading (Q17)','i',4,'Questions 14-19: Matching headings',1),
(@r2p2,'MATCHING_HEADINGS','Match heading (Q18)','iv',5,'Questions 14-19: Matching headings',1),
(@r2p2,'MATCHING_HEADINGS','Match heading (Q19)','viii',6,'Questions 14-19: Matching headings',1),
(@r2p2,'FILL_BLANK','Completion (Q20)','__(answer)',7,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q21)','__(answer)',8,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q22)','__(answer)',9,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q23)','__(answer)',10,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q24)','__(answer)',11,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q25)','__(answer)',12,'Questions 20-26: Completion',2),
(@r2p2,'FILL_BLANK','Completion (Q26)','__(answer)',13,'Questions 20-26: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'SCIENCE', 'PASSAGE_3', '[Cambridge 19 Test 2 Passage 3: Neuroscience of music]', 14, TRUE, 'ACADEMIC');
SET @r2p3 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r2p3,'MCQ','MCQ (Q27)','B',1,'Questions 27-31: MCQ',1),
(@r2p3,'MCQ','MCQ (Q28)','D',2,'Questions 27-31: MCQ',1),
(@r2p3,'MCQ','MCQ (Q29)','A',3,'Questions 27-31: MCQ',1),
(@r2p3,'MCQ','MCQ (Q30)','C',4,'Questions 27-31: MCQ',1),
(@r2p3,'MCQ','MCQ (Q31)','B',5,'Questions 27-31: MCQ',1),
(@r2p3,'YNNG','Statement (Q32)','YES',6,'Questions 32-36: YNNG',2),
(@r2p3,'YNNG','Statement (Q33)','NO',7,'Questions 32-36: YNNG',2),
(@r2p3,'YNNG','Statement (Q34)','NOT GIVEN',8,'Questions 32-36: YNNG',2),
(@r2p3,'YNNG','Statement (Q35)','YES',9,'Questions 32-36: YNNG',2),
(@r2p3,'YNNG','Statement (Q36)','NO',10,'Questions 32-36: YNNG',2),
(@r2p3,'FILL_BLANK','Completion (Q37)','__(answer)',11,'Questions 37-40: Completion',3),
(@r2p3,'FILL_BLANK','Completion (Q38)','__(answer)',12,'Questions 37-40: Completion',3),
(@r2p3,'FILL_BLANK','Completion (Q39)','__(answer)',13,'Questions 37-40: Completion',3),
(@r2p3,'FILL_BLANK','Completion (Q40)','__(answer)',14,'Questions 37-40: Completion',3);

-- ===== TEST 3 =====
INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'TECHNOLOGY', 'PASSAGE_1', '[Cambridge 19 Test 3 Passage 1: History of photography]', 13, TRUE, 'ACADEMIC');
SET @r3p1 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r3p1,'TFNG','Statement (Q1)','TRUE',1,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q2)','FALSE',2,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q3)','NOT GIVEN',3,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q4)','TRUE',4,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q5)','FALSE',5,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q6)','NOT GIVEN',6,'Questions 1-7: TFNG',1),
(@r3p1,'TFNG','Statement (Q7)','TRUE',7,'Questions 1-7: TFNG',1),
(@r3p1,'FILL_BLANK','Completion (Q8)','__(answer)',8,'Questions 8-13: Completion',2),
(@r3p1,'FILL_BLANK','Completion (Q9)','__(answer)',9,'Questions 8-13: Completion',2),
(@r3p1,'FILL_BLANK','Completion (Q10)','__(answer)',10,'Questions 8-13: Completion',2),
(@r3p1,'FILL_BLANK','Completion (Q11)','__(answer)',11,'Questions 8-13: Completion',2),
(@r3p1,'FILL_BLANK','Completion (Q12)','__(answer)',12,'Questions 8-13: Completion',2),
(@r3p1,'FILL_BLANK','Completion (Q13)','__(answer)',13,'Questions 8-13: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'SCIENCE', 'PASSAGE_2', '[Cambridge 19 Test 3 Passage 2: Soil science]', 13, TRUE, 'ACADEMIC');
SET @r3p2 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q14)','C',1,'Q14-19: Match paragraphs',1),
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q15)','E',2,'Q14-19: Match paragraphs',1),
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q16)','A',3,'Q14-19: Match paragraphs',1),
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q17)','D',4,'Q14-19: Match paragraphs',1),
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q18)','B',5,'Q14-19: Match paragraphs',1),
(@r3p2,'MATCHING_INFORMATION','Match paragraph (Q19)','F',6,'Q14-19: Match paragraphs',1),
(@r3p2,'FILL_BLANK','Completion (Q20)','__(answer)',7,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q21)','__(answer)',8,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q22)','__(answer)',9,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q23)','__(answer)',10,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q24)','__(answer)',11,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q25)','__(answer)',12,'Q20-26: Completion',2),
(@r3p2,'FILL_BLANK','Completion (Q26)','__(answer)',13,'Q20-26: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'EDUCATION', 'PASSAGE_3', '[Cambridge 19 Test 3 Passage 3: Language and thought]', 14, TRUE, 'ACADEMIC');
SET @r3p3 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r3p3,'MCQ','MCQ (Q27)','C',1,'Q27-31: MCQ',1),
(@r3p3,'MCQ','MCQ (Q28)','A',2,'Q27-31: MCQ',1),
(@r3p3,'MCQ','MCQ (Q29)','D',3,'Q27-31: MCQ',1),
(@r3p3,'MCQ','MCQ (Q30)','B',4,'Q27-31: MCQ',1),
(@r3p3,'MCQ','MCQ (Q31)','A',5,'Q27-31: MCQ',1),
(@r3p3,'YNNG','Statement (Q32)','YES',6,'Q32-36: YNNG',2),
(@r3p3,'YNNG','Statement (Q33)','NO',7,'Q32-36: YNNG',2),
(@r3p3,'YNNG','Statement (Q34)','NOT GIVEN',8,'Q32-36: YNNG',2),
(@r3p3,'YNNG','Statement (Q35)','YES',9,'Q32-36: YNNG',2),
(@r3p3,'YNNG','Statement (Q36)','NO',10,'Q32-36: YNNG',2),
(@r3p3,'FILL_BLANK','Completion (Q37)','__(answer)',11,'Q37-40: Completion',3),
(@r3p3,'FILL_BLANK','Completion (Q38)','__(answer)',12,'Q37-40: Completion',3),
(@r3p3,'FILL_BLANK','Completion (Q39)','__(answer)',13,'Q37-40: Completion',3),
(@r3p3,'FILL_BLANK','Completion (Q40)','__(answer)',14,'Q37-40: Completion',3);

-- ===== TEST 4 =====
INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'HISTORY', 'PASSAGE_1', '[Cambridge 19 Test 4 Passage 1: The history of bricks]', 13, TRUE, 'ACADEMIC');
SET @r4p1 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r4p1,'TFNG','Statement (Q1)','TRUE',1,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q2)','FALSE',2,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q3)','NOT GIVEN',3,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q4)','TRUE',4,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q5)','NOT GIVEN',5,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q6)','FALSE',6,'Q1-7: TFNG',1),
(@r4p1,'TFNG','Statement (Q7)','TRUE',7,'Q1-7: TFNG',1),
(@r4p1,'FILL_BLANK','Completion (Q8)','__(answer)',8,'Q8-13: Completion',2),
(@r4p1,'FILL_BLANK','Completion (Q9)','__(answer)',9,'Q8-13: Completion',2),
(@r4p1,'FILL_BLANK','Completion (Q10)','__(answer)',10,'Q8-13: Completion',2),
(@r4p1,'FILL_BLANK','Completion (Q11)','__(answer)',11,'Q8-13: Completion',2),
(@r4p1,'FILL_BLANK','Completion (Q12)','__(answer)',12,'Q8-13: Completion',2),
(@r4p1,'FILL_BLANK','Completion (Q13)','__(answer)',13,'Q8-13: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'HEALTH', 'PASSAGE_2', '[Cambridge 19 Test 4 Passage 2: Noise pollution]', 13, TRUE, 'ACADEMIC');
SET @r4p2 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q14)','D',1,'Q14-19: Match',1),
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q15)','A',2,'Q14-19: Match',1),
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q16)','F',3,'Q14-19: Match',1),
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q17)','B',4,'Q14-19: Match',1),
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q18)','E',5,'Q14-19: Match',1),
(@r4p2,'MATCHING_INFORMATION','Match paragraph (Q19)','C',6,'Q14-19: Match',1),
(@r4p2,'FILL_BLANK','Completion (Q20)','__(answer)',7,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q21)','__(answer)',8,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q22)','__(answer)',9,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q23)','__(answer)',10,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q24)','__(answer)',11,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q25)','__(answer)',12,'Q20-26: Completion',2),
(@r4p2,'FILL_BLANK','Completion (Q26)','__(answer)',13,'Q20-26: Completion',2);

INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type) VALUES
(NULL, 'SCIENCE', 'PASSAGE_3', '[Cambridge 19 Test 4 Passage 3: Migration patterns]', 14, TRUE, 'ACADEMIC');
SET @r4p3 = LAST_INSERT_ID();
INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id) VALUES
(@r4p3,'MCQ','MCQ (Q27)','B',1,'Q27-31: MCQ',1),
(@r4p3,'MCQ','MCQ (Q28)','D',2,'Q27-31: MCQ',1),
(@r4p3,'MCQ','MCQ (Q29)','A',3,'Q27-31: MCQ',1),
(@r4p3,'MCQ','MCQ (Q30)','C',4,'Q27-31: MCQ',1),
(@r4p3,'MCQ','MCQ (Q31)','B',5,'Q27-31: MCQ',1),
(@r4p3,'YNNG','Statement (Q32)','YES',6,'Q32-36: YNNG',2),
(@r4p3,'YNNG','Statement (Q33)','NOT GIVEN',7,'Q32-36: YNNG',2),
(@r4p3,'YNNG','Statement (Q34)','NO',8,'Q32-36: YNNG',2),
(@r4p3,'YNNG','Statement (Q35)','YES',9,'Q32-36: YNNG',2),
(@r4p3,'YNNG','Statement (Q36)','NOT GIVEN',10,'Q32-36: YNNG',2),
(@r4p3,'FILL_BLANK','Completion (Q37)','__(answer)',11,'Q37-40: Completion',3),
(@r4p3,'FILL_BLANK','Completion (Q38)','__(answer)',12,'Q37-40: Completion',3),
(@r4p3,'FILL_BLANK','Completion (Q39)','__(answer)',13,'Q37-40: Completion',3),
(@r4p3,'FILL_BLANK','Completion (Q40)','__(answer)',14,'Q37-40: Completion',3);
