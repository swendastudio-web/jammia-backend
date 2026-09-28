package com.jamia.backend.entity;

import java.time.LocalDate;

/**
 * How often members contribute (and how often someone receives the total).
 */
public enum ContributionFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY;

    // The due date of a cycle: the start date plus (cycle - 1) periods.
    public LocalDate dueDateOfCycle(LocalDate startDate, int cycleNumber) {
        int periodsAfterStart = cycleNumber - 1;
        return switch (this) {
            case WEEKLY -> startDate.plusWeeks(periodsAfterStart);
            case BIWEEKLY -> startDate.plusWeeks(2L * periodsAfterStart);
            case MONTHLY -> startDate.plusMonths(periodsAfterStart);
        };
    }
}
