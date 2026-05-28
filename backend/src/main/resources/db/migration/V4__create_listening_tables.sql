CREATE TABLE listening_parts (
    part_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    topic VARCHAR(100),
    audio_url VARCHAR(500) NOT NULL,
    transcript_text TEXT,
    duration_seconds INT,
    INDEX idx_lp_part (part_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE listening_questions (
    question_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_id BIGINT NOT NULL,
    question_type VARCHAR(15) NOT NULL,
    question_text TEXT NOT NULL,
    correct_answer VARCHAR(500) NOT NULL,
    order_index INT NOT NULL,
    FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE listening_tests (
    test_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    test_mode VARCHAR(15) NOT NULL,
    score DECIMAL(2,1),
    total_questions INT,
    correct_answers INT,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_lt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE listening_test_parts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_id BIGINT NOT NULL,
    part_id BIGINT NOT NULL,
    user_answers_json JSON,
    FOREIGN KEY (test_id) REFERENCES listening_tests(test_id) ON DELETE CASCADE,
    FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
