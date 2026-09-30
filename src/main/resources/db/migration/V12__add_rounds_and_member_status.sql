-- Phase A: a room runs in ROUNDS. One round = everyone receives once.
-- After a round ends the room is OPEN again, everyone stays, and the admin can start the next round.
CREATE TABLE room_rounds (
    id                BIGSERIAL   PRIMARY KEY,
    room_id           BIGINT      NOT NULL REFERENCES savings_rooms (id) ON DELETE CASCADE,
    round_number      INTEGER     NOT NULL,
    status            VARCHAR(20) NOT NULL,   -- ACTIVE, COMPLETED
    turn_order_method VARCHAR(20) NOT NULL,   -- RANDOM, MANUAL
    started_at        TIMESTAMP   NOT NULL,   -- turn 1 begins
    ends_at           TIMESTAMP   NOT NULL,   -- the last turn ends
    completed_at      TIMESTAMP,
    CONSTRAINT uk_room_rounds_room_number UNIQUE (room_id, round_number)
);

-- A room can have only one running round at a time.
CREATE UNIQUE INDEX uk_room_rounds_one_active ON room_rounds (room_id) WHERE status = 'ACTIVE';

-- Existing started rooms become "Round 1" (from their old start date and schedule).
INSERT INTO room_rounds (room_id, round_number, status, turn_order_method, started_at, ends_at, completed_at)
SELECT r.id,
       1,
       CASE WHEN r.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'COMPLETED' END,
       COALESCE(r.turn_order_method, 'RANDOM'),
       r.start_date::timestamp,
       (SELECT MAX(c.due_date) FROM contributions c WHERE c.room_id = r.id)::timestamp
           + CASE r.frequency WHEN 'WEEKLY' THEN INTERVAL '7 days'
                              WHEN 'BIWEEKLY' THEN INTERVAL '14 days'
                              ELSE INTERVAL '1 month' END,
       CASE WHEN r.status = 'COMPLETED' THEN now() END
FROM savings_rooms r
WHERE r.status IN ('ACTIVE', 'COMPLETED') AND r.start_date IS NOT NULL;

-- Payments belong to a round, and are due at an exact time (needed for short test periods).
ALTER TABLE contributions ADD COLUMN round_id BIGINT REFERENCES room_rounds (id) ON DELETE CASCADE;
ALTER TABLE contributions ADD COLUMN due_at TIMESTAMP;
UPDATE contributions c
SET round_id = (SELECT rr.id FROM room_rounds rr WHERE rr.room_id = c.room_id AND rr.round_number = 1),
    due_at   = c.due_date::timestamp;
ALTER TABLE contributions ALTER COLUMN round_id SET NOT NULL;
ALTER TABLE contributions ALTER COLUMN due_at SET NOT NULL;
ALTER TABLE contributions DROP CONSTRAINT uk_contributions_room_cycle_payer;
ALTER TABLE contributions DROP COLUMN due_date;
ALTER TABLE contributions
    ADD CONSTRAINT uk_contributions_round_cycle_payer UNIQUE (round_id, cycle_number, payer_member_id);

-- Members are never deleted (their payment history must stay correct):
-- ACTIVE, LEFT (they left) or REMOVED (the admin removed them).
ALTER TABLE room_members ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE room_members ADD COLUMN left_at TIMESTAMP;

-- Rooms whose round is finished are OPEN again; turns are decided again for the next round.
UPDATE room_members SET turn_position = NULL
WHERE room_id IN (SELECT id FROM savings_rooms WHERE status = 'COMPLETED');
UPDATE savings_rooms SET status = 'OPEN' WHERE status = 'COMPLETED';

-- The start date and turn-order method now belong to each round.
ALTER TABLE savings_rooms DROP COLUMN start_date;
ALTER TABLE savings_rooms DROP COLUMN turn_order_method;
