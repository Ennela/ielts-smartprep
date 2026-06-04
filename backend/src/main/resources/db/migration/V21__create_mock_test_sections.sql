-- V21: Create Mock Test Sections Table
CREATE TABLE mock_test_sections (
    section_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mock_test_id BIGINT NOT NULL,
    section_type VARCHAR(20) NOT NULL,
    duration_seconds INT NOT NULL,
    section_order INT NOT NULL,
    FOREIGN KEY (mock_test_id) REFERENCES mock_tests(mock_test_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed sections for mock test ID 1 (Cambridge IELTS 18 Test 1)
INSERT INTO mock_test_sections (mock_test_id, section_type, duration_seconds, section_order) VALUES (1, 'LISTENING', 2400, 1);
INSERT INTO mock_test_sections (mock_test_id, section_type, duration_seconds, section_order) VALUES (1, 'READING', 3600, 2);
INSERT INTO mock_test_sections (mock_test_id, section_type, duration_seconds, section_order) VALUES (1, 'WRITING', 3600, 3);
