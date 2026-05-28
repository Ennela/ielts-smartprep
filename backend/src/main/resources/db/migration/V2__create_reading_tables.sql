CREATE TABLE reading_quizzes (
    quiz_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic VARCHAR(20) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    passage_text TEXT NOT NULL,
    time_limit_seconds INT,
    score DECIMAL(2,1),
    total_questions INT DEFAULT 5,
    correct_answers INT,
    submitted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_rq_user (user_id),
    INDEX idx_rq_topic_diff (topic, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reading_questions (
    question_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    question_type VARCHAR(10) NOT NULL,
    question_text TEXT NOT NULL,
    option_a VARCHAR(500),
    option_b VARCHAR(500),
    option_c VARCHAR(500),
    option_d VARCHAR(500),
    correct_answer VARCHAR(500) NOT NULL,
    user_answer VARCHAR(500),
    explanation TEXT,
    order_index INT NOT NULL,
    FOREIGN KEY (quiz_id) REFERENCES reading_quizzes(quiz_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
