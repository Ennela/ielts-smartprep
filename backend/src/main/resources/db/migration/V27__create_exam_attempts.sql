-- Server-authoritative exam attempt tracking for timer enforcement
CREATE TABLE IF NOT EXISTS exam_attempts (
    attempt_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    skill_type          VARCHAR(15) NOT NULL,
    duration_seconds    INT NOT NULL,
    started_at          DATETIME(6) NOT NULL,
    deadline            DATETIME(6) NOT NULL,
    status              VARCHAR(15) NOT NULL DEFAULT 'IN_PROGRESS',
    auto_submitted      TINYINT(1) DEFAULT 0,
    time_spent_seconds  INT NULL,
    time_spent_task1    INT NULL,
    time_spent_task2    INT NULL,
    exam_reference_ids  TEXT NULL,
    created_at          DATETIME(6) NOT NULL,
    submitted_at        DATETIME(6) NULL,
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_attempt_user_skill_status (user_id, skill_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
