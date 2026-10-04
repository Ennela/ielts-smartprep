-- V57: Let an admin suspend an account.
--
-- A suspended user cannot log in, and the session they already have stops working: the
-- JWT filter no longer authenticates them and the refresh endpoint refuses them. Kept
-- separate from the temporary lockout after failed passwords (LoginLockoutService), which
-- lives in Redis and lifts itself.
ALTER TABLE users
    ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE;
