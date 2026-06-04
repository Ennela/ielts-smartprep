-- V22: Create Vocabulary Builder Table

CREATE TABLE vocabulary (
    vocab_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    word VARCHAR(100) NOT NULL,
    phonetic VARCHAR(100),
    part_of_speech VARCHAR(50),
    meaning_vi TEXT NOT NULL,
    example TEXT,
    collocation TEXT,
    ease_factor DOUBLE NOT NULL DEFAULT 2.5,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    due_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_skill VARCHAR(50),
    source_ref VARCHAR(100),
    cefr_level VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY idx_user_word (user_id, word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
