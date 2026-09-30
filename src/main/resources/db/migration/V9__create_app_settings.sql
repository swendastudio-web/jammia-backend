-- JAMIA-wide settings that the admin can change (not hard-coded).
-- Always exactly ONE row (id = 1). New settings are added as new columns.
CREATE TABLE app_settings (
    id                     SMALLINT PRIMARY KEY CHECK (id = 1),
    invite_link_valid_days INTEGER  NOT NULL CHECK (invite_link_valid_days BETWEEN 1 AND 90)
);

INSERT INTO app_settings (id, invite_link_valid_days) VALUES (1, 7);
