-- Optional phone number on the user profile, in international format (e.g. +96891234567).
-- Not unique: family members may share a number.
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
