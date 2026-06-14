-- V25: Add avatar_url column to users table
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) NULL;
