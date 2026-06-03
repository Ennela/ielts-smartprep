CREATE TABLE user_answers (
    answer_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    history_id     BIGINT NOT NULL,
    question_no    INT NOT NULL,
    question_text  TEXT NOT NULL,
    question_type  VARCHAR(30) NOT NULL,
    user_answer    VARCHAR(500),
    correct_answer VARCHAR(500) NOT NULL,
    is_correct     BOOLEAN NOT NULL DEFAULT FALSE,
    explanation    TEXT,
    options_json   TEXT,
    FOREIGN KEY (history_id) REFERENCES score_history(history_id) ON DELETE CASCADE,
    INDEX idx_ua_history (history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
