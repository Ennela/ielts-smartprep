-- V14: Add IELTS Full Mock Test Tables

-- 1. Mock Test Template Table
CREATE TABLE mock_tests (
    mock_test_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    difficulty VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Mock Test Component Link Tables (Ordered)
CREATE TABLE mock_test_listening_parts (
    mock_test_id BIGINT NOT NULL,
    part_id BIGINT NOT NULL,
    part_order INT NOT NULL,
    PRIMARY KEY (mock_test_id, part_id),
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id) ON DELETE CASCADE,
    FOREIGN KEY (part_id) REFERENCES listening_parts(part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mock_test_reading_quizzes (
    mock_test_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    passage_order INT NOT NULL,
    PRIMARY KEY (mock_test_id, quiz_id),
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id) ON DELETE CASCADE,
    FOREIGN KEY (quiz_id) REFERENCES reading_quizzes(quiz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mock_test_writing_prompts (
    mock_test_id BIGINT NOT NULL,
    prompt_id BIGINT NOT NULL,
    prompt_order INT NOT NULL,
    PRIMARY KEY (mock_test_id, prompt_id),
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id) ON DELETE CASCADE,
    FOREIGN KEY (prompt_id) REFERENCES writing_prompts(prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Mock Test Active Session Table (Autosave & Resume State)
CREATE TABLE mock_test_sessions (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mock_test_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_section VARCHAR(20) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    section_started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    time_remaining_seconds INT NOT NULL,
    progress_json LONGTEXT,
    last_synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id),
    INDEX idx_user_active_session (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Mock Test Submission (Final Results) Table
CREATE TABLE mock_test_submissions (
    submission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mock_test_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    listening_score DECIMAL(2, 1),
    reading_score DECIMAL(2, 1),
    writing_score DECIMAL(2, 1),
    overall_band DECIMAL(2, 1),
    listening_correct_answers INT,
    reading_correct_answers INT,
    writing_task1_submission_id BIGINT,
    writing_task2_submission_id BIGINT,
    listening_test_id BIGINT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id),
    FOREIGN KEY (writing_task1_submission_id) REFERENCES writing_submissions(submission_id) ON DELETE SET NULL,
    FOREIGN KEY (writing_task2_submission_id) REFERENCES writing_submissions(submission_id) ON DELETE SET NULL,
    FOREIGN KEY (listening_test_id) REFERENCES listening_tests(test_id) ON DELETE SET NULL,
    INDEX idx_user_submissions (user_id, submitted_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Seed an Initial Mock Test for testing (Cambridge 18 Academic Mock Test 1)
INSERT INTO mock_tests (title, description, difficulty) 
VALUES ('Cambridge IELTS 18 Test 1', 'Full academic practice exam covering Listening Parts 1-4, Reading Passages 1-3, and Writing Tasks 1 & 2.', 'MEDIUM');

-- Link Listening Parts (IDs 1, 3, 5, 7 correspond to Part Numbers 1, 2, 3, 4)
INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES (1, 1, 1);
INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES (1, 3, 2);
INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES (1, 5, 3);
INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES (1, 7, 4);

-- Link Writing Prompts (Task 1: ID 7, Task 2: ID 1)
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (1, 7, 1);
INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order) VALUES (1, 1, 2);
