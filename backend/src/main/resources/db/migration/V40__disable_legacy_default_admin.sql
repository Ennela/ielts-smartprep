-- V11 seeded a public default administrator credential. Disable only the unchanged
-- legacy seed; administrators whose password has already been rotated are untouched.
-- The digest comparison avoids copying the compromised password hash into this migration.
UPDATE users
SET password_hash = '!disabled-legacy-default-admin!'
WHERE email = 'admin@smartprep.local'
  AND username = 'admin'
  AND SHA2(password_hash, 256) = '8ea4f8a3468a0ebeae4b2eda95137c88f3eeb3c6a5970047a9a8e25f23ba9519';
