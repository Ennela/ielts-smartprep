-- V58: IELTS Speaking practice.
--
-- A prompt is one task of one Part: Part 1 and Part 3 hold a few questions on a topic
-- (question_text, one per line); Part 2 holds a cue card (question_text is the task,
-- cue_points the "You should say" points, one per line).
--
-- A submission is one recorded answer. The recording is kept in object storage under
-- audio_key; Gemini listens to it and returns a transcript and the four Speaking bands.
-- The overall band is their average with IELTS rounding, as in Writing.
CREATE TABLE IF NOT EXISTS speaking_prompts (
    prompt_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    part          INT NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    question_text TEXT NOT NULL,
    cue_points    TEXT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_speaking_prompts_part (part)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS speaking_submissions (
    submission_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    prompt_id          BIGINT NOT NULL,
    audio_key          VARCHAR(255) NOT NULL,
    audio_mime_type    VARCHAR(50) NOT NULL,
    duration_seconds   INT NOT NULL,
    transcript         TEXT NULL,
    overall_band       DECIMAL(2,1) NOT NULL,
    fluency_band       DECIMAL(2,1) NOT NULL,
    lexical_band       DECIMAL(2,1) NOT NULL,
    grammar_band       DECIMAL(2,1) NOT NULL,
    pronunciation_band DECIMAL(2,1) NOT NULL,
    feedback_json      TEXT NOT NULL,
    submitted_at       DATETIME(6) NOT NULL,
    CONSTRAINT fk_speaking_sub_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_speaking_sub_prompt FOREIGN KEY (prompt_id) REFERENCES speaking_prompts(prompt_id),
    INDEX idx_speaking_sub_user_date (user_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Starter prompts, written for this app in the style of the test.
INSERT INTO speaking_prompts (part, topic, question_text, cue_points) VALUES
(1, 'Hometown', 'Where is your hometown?\nWhat do you like most about it?\nHas it changed much since you were a child?\nWould you like to live there in the future?', NULL),
(1, 'Work or studies', 'Do you work or are you a student?\nWhy did you choose that job or subject?\nWhat is the most interesting part of it?\nWhat would you like to do in the next few years?', NULL),
(1, 'Free time', 'What do you usually do in your free time?\nDo you prefer spending free time alone or with others?\nIs there a hobby you would like to try?\nHow did you spend your free time as a child?', NULL),
(1, 'Reading', 'Do you enjoy reading?\nWhat kind of things do you read most often?\nDo you prefer paper books or reading on a screen?\nDid you read a lot when you were younger?', NULL),
(1, 'Weather', 'What is the weather usually like where you live?\nWhat is your favourite kind of weather?\nDoes the weather ever change your plans?\nWould you like to live somewhere with a different climate?', NULL),
(1, 'Technology', 'How often do you use a computer or phone?\nWhat do you mostly use it for?\nIs there an app you could not live without?\nDo you think people spend too much time on their phones?', NULL),
(2, 'A person who taught you something', 'Describe a person who taught you something important.', 'who the person is\nhow you know them\nwhat they taught you\nand explain why it was important to you'),
(2, 'A place you would like to visit', 'Describe a place you would like to visit in the future.', 'where it is\nhow you learned about it\nwhat you would do there\nand explain why you want to go there'),
(2, 'A useful object', 'Describe an object you use every day that is important to you.', 'what it is\nwhen and how you got it\nhow often you use it\nand explain why it is important to you'),
(2, 'A memorable journey', 'Describe a journey you remember well.', 'where you went\nwho you went with\nwhat happened during the journey\nand explain why you remember it'),
(3, 'Education and learning', 'Who do you think has more influence on young people, teachers or parents?\nHow has technology changed the way people learn?\nShould everyone go to university? Why or why not?\nWhat skills will be most important for students in the future?', NULL),
(3, 'Travel and tourism', 'Why do people enjoy travelling to other countries?\nWhat are the advantages and disadvantages of tourism for a local community?\nHow might travel change in the next twenty years?\nIs it better to travel independently or with a tour group?', NULL),
(3, 'Technology and society', 'Has technology made people more or less sociable?\nWhat problems can arise when people rely too much on technology?\nShould children be limited in how much they use screens?\nWho should be responsible for protecting personal data online?', NULL),
(3, 'Work and careers', 'What makes a job satisfying?\nIs it better to stay in one job for a long time or to change jobs often?\nHow do you think working life will change in the future?\nShould governments help people retrain for new careers?', NULL);
