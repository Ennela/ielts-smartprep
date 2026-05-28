CREATE TABLE writing_prompts (
    prompt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_text TEXT NOT NULL,
    essay_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE writing_submissions (
    submission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    prompt_id BIGINT NOT NULL,
    essay_text TEXT NOT NULL,
    word_count INT,
    overall_band DECIMAL(2,1),
    task_response_score DECIMAL(2,1),
    coherence_score DECIMAL(2,1),
    lexical_score DECIMAL(2,1),
    grammar_score DECIMAL(2,1),
    error_list_json JSON,
    rewritten_version TEXT,
    ai_feedback TEXT,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (prompt_id) REFERENCES writing_prompts(prompt_id) ON DELETE CASCADE,
    INDEX idx_ws_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed data: de bai Writing Task 2 mau
INSERT INTO writing_prompts (prompt_text, essay_type) VALUES
('Some people think that the best way to reduce crime is to give longer prison sentences. Others, however, believe there are better alternative ways of reducing crime. Discuss both views and give your opinion.', 'DISCUSSION'),
('In many countries, the amount of crime is increasing. What do you think are the main causes of crime? How can we deal with those causes?', 'CAUSE_AND_EFFECT'),
('Some people believe that unpaid community service should be a compulsory part of high school programmes. To what extent do you agree or disagree?', 'OPINION'),
('The increasing use of technology is changing the way people interact with each other. Do you think this has more positive or negative effects on individuals and society?', 'DISCUSSION'),
('Some people say that advertising encourages us to buy things we really do not need. Others say that advertisements tell us about new products that may improve our lives. Which viewpoint do you agree with?', 'OPINION'),
('In some countries, an increasing number of people are suffering from health problems as a result of eating too much fast food. It is therefore necessary for governments to impose a higher tax on this kind of food. To what extent do you agree or disagree?', 'OPINION');
