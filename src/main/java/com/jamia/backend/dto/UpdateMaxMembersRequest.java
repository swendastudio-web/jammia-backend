package com.jamia.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The admin changes how many members the room may have (only between rounds).
 */
public record UpdateMaxMembersRequest(

        @NotNull(message = "Maximum members is required")
        @Min(value = 2, message = "A room needs at least 2 members")
        Integer maxMembers
) {
}
