CREATE TABLE score_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skill_type VARCHAR(15) NOT NULL,
    score DECIMAL(2,1) NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_sh_user_skill_date (user_id, skill_type, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
