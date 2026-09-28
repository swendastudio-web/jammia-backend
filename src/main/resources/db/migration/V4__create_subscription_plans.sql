-- Subscription plans. The member limits live here (not in the code),
-- so the JAMIA admin can change them without a new app release.
CREATE TABLE subscription_plans (
    id                   BIGSERIAL   PRIMARY KEY,
    code                 VARCHAR(20) NOT NULL UNIQUE,
    max_members_per_room INTEGER     NOT NULL CHECK (max_members_per_room >= 2)
);

-- Starting values (examples from the master prompt; the admin can change them).
INSERT INTO subscription_plans (code, max_members_per_room) VALUES
    ('FREE', 5),
    ('SILVER', 20),
    ('GOLD', 100);

-- Every user has a plan. Existing and new users start on FREE.
ALTER TABLE users ADD COLUMN subscription_plan_id BIGINT REFERENCES subscription_plans (id);
UPDATE users SET subscription_plan_id = (SELECT id FROM subscription_plans WHERE code = 'FREE');
ALTER TABLE users ALTER COLUMN subscription_plan_id SET NOT NULL;
