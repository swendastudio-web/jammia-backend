package com.jamia.backend.entity;

/**
 * The state of one round (rotation) of a room.
 */
public enum RoundStatus {
    // Running: the turn moves to the next person by the clock.
    ACTIVE,
    // Everyone's turn has passed. Kept as history.
    COMPLETED
}
