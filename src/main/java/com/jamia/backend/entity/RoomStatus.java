package com.jamia.backend.entity;

/**
 * The life of a savings room. It goes OPEN -> ACTIVE -> OPEN -> ACTIVE ... one round at a time.
 */
public enum RoomStatus {
    // Between rounds (or before the first): people can join, the admin can change the size,
    // remove members, and start the next round; members can leave.
    OPEN,
    // A round is running: the turn moves by the clock; nobody can join or leave.
    ACTIVE
}
