package com.jamia.backend.entity;

/**
 * The state of a request to join a room.
 */
public enum JoinRequestStatus {
    // Waiting for the room creator.
    PENDING,
    // Accepted: the person is now a member.
    APPROVED,
    // Declined by the room creator.
    REJECTED
}
