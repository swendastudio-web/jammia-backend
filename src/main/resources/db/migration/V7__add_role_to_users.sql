-- Each user has a role: USER (everyone) or ADMIN (JAMIA staff).
-- Existing and new users are USER. An ADMIN is set directly in the database for now:
--   UPDATE users SET role = 'ADMIN' WHERE email = '...';
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
