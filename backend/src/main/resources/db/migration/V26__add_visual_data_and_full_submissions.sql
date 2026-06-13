-- Add visual_data to writing_prompts
ALTER TABLE writing_prompts ADD COLUMN visual_data TEXT NULL;

-- Widen essay_type to support 'LETTER'
ALTER TABLE writing_prompts MODIFY COLUMN essay_type VARCHAR(50) NOT NULL;

-- Create writing_full_submissions table
CREATE TABLE writing_full_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task1_submission_id BIGINT NOT NULL,
    task2_submission_id BIGINT NOT NULL,
    overall_band DECIMAL(2,1) NOT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (task1_submission_id) REFERENCES writing_submissions(submission_id) ON DELETE CASCADE,
    FOREIGN KEY (task2_submission_id) REFERENCES writing_submissions(submission_id) ON DELETE CASCADE,
    INDEX idx_wfs_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
