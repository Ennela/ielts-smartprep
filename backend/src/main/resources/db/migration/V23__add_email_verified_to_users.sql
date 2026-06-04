ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark existing users as verified (they were already active before this feature)
UPDATE users SET email_verified = TRUE;
