-- ============================================================
-- V16: Refactor MCQ option storage
-- ============================================================

-- 1. Create the unified question_options table
CREATE TABLE question_options (
    option_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reading_question_id BIGINT NULL,
    listening_question_id BIGINT NULL,
    label VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL,
    FOREIGN KEY (reading_question_id) REFERENCES reading_questions(question_id) ON DELETE CASCADE,
    FOREIGN KEY (listening_question_id) REFERENCES listening_questions(question_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Migrate existing Listening MCQ questions stored with '\n'
DELIMITER //

CREATE PROCEDURE MigrateListeningMcq()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE q_id BIGINT;
    DECLARE q_text TEXT;
    DECLARE q_correct VARCHAR(10);
    DECLARE cur CURSOR FOR SELECT question_id, question_text, correct_answer FROM listening_questions WHERE question_type = 'MCQ';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO q_id, q_text, q_correct;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- Clean carriage returns if any
        SET q_text = REPLACE(q_text, '\r', '');

        -- First line is the stem
        SET @stem = SUBSTRING_INDEX(q_text, '\n', 1);

        -- Update the question text to only contain the stem
        UPDATE listening_questions SET question_text = @stem WHERE question_id = q_id;

        -- Extract options
        SET @opt_a = SUBSTRING_INDEX(SUBSTRING_INDEX(q_text, '\n', 2), '\n', -1);
        SET @opt_b = SUBSTRING_INDEX(SUBSTRING_INDEX(q_text, '\n', 3), '\n', -1);
        SET @opt_c = SUBSTRING_INDEX(SUBSTRING_INDEX(q_text, '\n', 4), '\n', -1);
        SET @opt_d = SUBSTRING_INDEX(SUBSTRING_INDEX(q_text, '\n', 5), '\n', -1);

        -- Count total newlines to know how many options there are
        SET @newline_count = LENGTH(q_text) - LENGTH(REPLACE(q_text, '\n', ''));

        IF @newline_count >= 1 THEN
            INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
            VALUES (q_id, 'A', TRIM(SUBSTRING(@opt_a, 3)), (q_correct = 'A'), 1);
        END IF;
        IF @newline_count >= 2 THEN
            INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
            VALUES (q_id, 'B', TRIM(SUBSTRING(@opt_b, 3)), (q_correct = 'B'), 2);
        END IF;
        IF @newline_count >= 3 THEN
            INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
            VALUES (q_id, 'C', TRIM(SUBSTRING(@opt_c, 3)), (q_correct = 'C'), 3);
        END IF;
        IF @newline_count >= 4 THEN
            INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
            VALUES (q_id, 'D', TRIM(SUBSTRING(@opt_d, 3)), (q_correct = 'D'), 4);
        END IF;
    END LOOP;
    CLOSE cur;
END //

DELIMITER ;

CALL MigrateListeningMcq();
DROP PROCEDURE MigrateListeningMcq;

-- 3. Migrate existing Reading MCQ questions stored in option_a through option_d
INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
SELECT question_id, 'A', option_a, (correct_answer = 'A'), 1 FROM reading_questions WHERE question_type = 'MCQ' AND option_a IS NOT NULL AND option_a <> '';

INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
SELECT question_id, 'B', option_b, (correct_answer = 'B'), 2 FROM reading_questions WHERE question_type = 'MCQ' AND option_b IS NOT NULL AND option_b <> '';

INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
SELECT question_id, 'C', option_c, (correct_answer = 'C'), 3 FROM reading_questions WHERE question_type = 'MCQ' AND option_c IS NOT NULL AND option_c <> '';

INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
SELECT question_id, 'D', option_d, (correct_answer = 'D'), 4 FROM reading_questions WHERE question_type = 'MCQ' AND option_d IS NOT NULL AND option_d <> '';

-- 4. Clean up deprecated columns from reading_questions
ALTER TABLE reading_questions DROP COLUMN option_a;
ALTER TABLE reading_questions DROP COLUMN option_b;
ALTER TABLE reading_questions DROP COLUMN option_c;
ALTER TABLE reading_questions DROP COLUMN option_d;
