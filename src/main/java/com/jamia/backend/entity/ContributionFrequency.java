package com.jamia.backend.entity;

import java.time.LocalDateTime;

/**
 * How often the turn moves to the next person.
 */
public enum ContributionFrequency {
    // For testing only: allowed only when the backend runs with the "dev" switch (never in production).
    FIVE_MINUTES,
    WEEKLY,
    BIWEEKLY,
    MONTHLY;

    public boolean isDevelopmentOnly() {
        return this == FIVE_MINUTES;
    }

    // When a turn (cycle) begins: the round's start plus (cycle - 1) periods.
    // Cycle "count + 1" gives the moment the last turn ends.
    public LocalDateTime startOfCycle(LocalDateTime roundStart, int cycleNumber) {
        int periodsAfterStart = cycleNumber - 1;
        return switch (this) {
            case FIVE_MINUTES -> roundStart.plusMinutes(5L * periodsAfterStart);
            case WEEKLY -> roundStart.plusWeeks(periodsAfterStart);
            case BIWEEKLY -> roundStart.plusWeeks(2L * periodsAfterStart);
            case MONTHLY -> roundStart.plusMonths(periodsAfterStart);
        };
    }
}
