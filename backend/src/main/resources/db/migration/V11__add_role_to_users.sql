-- Add role column to users table
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'STUDENT';

-- Seed default admin account (password: admin123 hashed with BCrypt 12 rounds)
-- NOTE: Change this password immediately after first login in production
INSERT IGNORE INTO users (email, username, password_hash, display_name, role)
VALUES ('admin@smartprep.local', 'admin', '$2a$12$byPbRBX6sKZ3lZuEnmVcU.DlFNWszDB1gP5savEGsAscLrQQvec3i', 'Administrator', 'ADMIN');
