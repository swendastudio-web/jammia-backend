package com.jamia.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Admin: the new biggest room size for a plan.
 */
public record UpdatePlanLimitRequest(

        @NotNull(message = "Maximum members per room is required")
        @Min(value = 2, message = "A room needs at least 2 members")
        @Max(value = 1000, message = "Maximum members per room can be at most 1000")
        Integer maxMembersPerRoom
) {
}
