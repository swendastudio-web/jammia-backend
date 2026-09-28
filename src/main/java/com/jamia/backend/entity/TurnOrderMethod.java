package com.jamia.backend.entity;

/**
 * How the turn order was decided when the room started.
 */
public enum TurnOrderMethod {
    // The app shuffled the members randomly.
    RANDOM,
    // The creator chose the order.
    MANUAL
}
