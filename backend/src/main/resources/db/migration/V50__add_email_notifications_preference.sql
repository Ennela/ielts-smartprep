-- V50: Store the email-notification preference instead of keeping it in one browser.
--
-- The Preferences tab has always had this toggle, but it only ever reached localStorage:
-- the setting vanished on another device and the server had no way to know whether a
-- learner wanted to be emailed at all. It now travels with the account.
--
-- Defaults to TRUE so existing accounts keep the behaviour the toggle showed them.

ALTER TABLE users
    ADD COLUMN email_notifications BOOLEAN NOT NULL DEFAULT TRUE;
