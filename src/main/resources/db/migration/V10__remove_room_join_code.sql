-- Rooms are now joined through members' invite links + the creator's approval,
-- so the old join code (which let anyone in without approval) is removed.
ALTER TABLE savings_rooms DROP COLUMN join_code;
