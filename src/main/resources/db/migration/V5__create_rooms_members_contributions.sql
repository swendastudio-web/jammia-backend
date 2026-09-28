-- A savings room: a group that contributes the same amount every period,
-- and one member receives the total each period (their "turn").
CREATE TABLE savings_rooms (
    id                  BIGSERIAL      PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL,
    description         VARCHAR(500),
    contribution_amount NUMERIC(12, 2) NOT NULL CHECK (contribution_amount > 0),
    currency            VARCHAR(3)     NOT NULL,
    frequency           VARCHAR(20)    NOT NULL,   -- WEEKLY, BIWEEKLY, MONTHLY
    max_members         INTEGER        NOT NULL CHECK (max_members >= 2),
    creator_id          BIGINT         NOT NULL REFERENCES users (id),
    join_code           VARCHAR(12)    NOT NULL UNIQUE,
    status              VARCHAR(20)    NOT NULL,   -- OPEN, ACTIVE, COMPLETED
    turn_order_method   VARCHAR(20),               -- RANDOM or MANUAL, set when the room starts
    start_date          DATE,                      -- first due date, set when the room starts
    created_at          TIMESTAMP      NOT NULL
);

-- Who belongs to which room, and their turn (1 = receives first).
CREATE TABLE room_members (
    id            BIGSERIAL PRIMARY KEY,
    room_id       BIGINT    NOT NULL REFERENCES savings_rooms (id) ON DELETE CASCADE,
    user_id       BIGINT    NOT NULL REFERENCES users (id),
    turn_position INTEGER,                         -- set when the room starts
    joined_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_room_members_room_user UNIQUE (room_id, user_id),
    CONSTRAINT uk_room_members_room_turn UNIQUE (room_id, turn_position)
);

CREATE INDEX idx_room_members_user ON room_members (user_id);

-- One row = one member's payment for one cycle, to that cycle's recipient.
-- JAMIA does not move money: this only tracks the status people report.
CREATE TABLE contributions (
    id                  BIGSERIAL      PRIMARY KEY,
    room_id             BIGINT         NOT NULL REFERENCES savings_rooms (id) ON DELETE CASCADE,
    cycle_number        INTEGER        NOT NULL,
    due_date            DATE           NOT NULL,
    payer_member_id     BIGINT         NOT NULL REFERENCES room_members (id),
    recipient_member_id BIGINT         NOT NULL REFERENCES room_members (id),
    amount              NUMERIC(12, 2) NOT NULL,
    status              VARCHAR(20)    NOT NULL,   -- PENDING, PAID, CONFIRMED
    paid_at             TIMESTAMP,
    confirmed_at        TIMESTAMP,
    CONSTRAINT uk_contributions_room_cycle_payer UNIQUE (room_id, cycle_number, payer_member_id)
);
