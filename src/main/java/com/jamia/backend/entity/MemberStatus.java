package com.jamia.backend.entity;

/**
 * A member's place in a room. Members are never deleted, so their payment history stays correct.
 */
public enum MemberStatus {
    ACTIVE,
    // The member left the room (between rounds).
    LEFT,
    // The room admin removed the member.
    REMOVED
}
