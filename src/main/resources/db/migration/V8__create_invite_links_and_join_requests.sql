-- A member's personal invite link for a room. The token is the secret part of the link
-- (https://<domain>/invite/<token>). Whoever joins through it is "referred by" that member.
CREATE TABLE room_invite_links (
    id                   BIGSERIAL   PRIMARY KEY,
    room_id              BIGINT      NOT NULL REFERENCES savings_rooms (id) ON DELETE CASCADE,
    created_by_member_id BIGINT      NOT NULL REFERENCES room_members (id) ON DELETE CASCADE,
    token                VARCHAR(64) NOT NULL UNIQUE,
    expires_at           TIMESTAMP   NOT NULL,
    created_at           TIMESTAMP   NOT NULL
);

-- A person asking to join a room through a member's link. Only the room creator decides.
CREATE TABLE join_requests (
    id                     BIGSERIAL   PRIMARY KEY,
    room_id                BIGINT      NOT NULL REFERENCES savings_rooms (id) ON DELETE CASCADE,
    user_id                BIGINT      NOT NULL REFERENCES users (id),
    referred_by_member_id  BIGINT      NOT NULL REFERENCES room_members (id),
    status                 VARCHAR(20) NOT NULL,   -- PENDING, APPROVED, REJECTED
    created_at             TIMESTAMP   NOT NULL,
    decided_at             TIMESTAMP
);

-- A person can have only ONE open (pending) request per room.
-- (Older approved/rejected requests don't count, so they can ask again after a rejection.)
CREATE UNIQUE INDEX uk_join_requests_one_pending
    ON join_requests (room_id, user_id) WHERE status = 'PENDING';
