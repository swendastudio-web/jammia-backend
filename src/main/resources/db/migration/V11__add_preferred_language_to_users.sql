-- The language the user chose in the app (e.g. "en", "ar", "fr", "ru", "am").
-- Kept in the account so it follows the user to a new phone, and future reminders can use it.
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(10) NOT NULL DEFAULT 'en';
