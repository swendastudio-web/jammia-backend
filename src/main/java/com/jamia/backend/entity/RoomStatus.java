package com.jamia.backend.entity;

/**
 * The life of a savings room.
 */
public enum RoomStatus {
    // Accepting members; turn order not decided yet.
    OPEN,
    // Started: turn order is fixed, contributions are running, nobody can join.
    ACTIVE,
    // Every contribution of every cycle has been confirmed.
    COMPLETED
}
