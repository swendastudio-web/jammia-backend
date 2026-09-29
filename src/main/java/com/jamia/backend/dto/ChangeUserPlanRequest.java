package com.jamia.backend.dto;

import com.jamia.backend.entity.SubscriptionPlanCode;
import jakarta.validation.constraints.NotNull;

/**
 * Admin: the plan to give a user (FREE, SILVER or GOLD).
 */
public record ChangeUserPlanRequest(

        @NotNull(message = "Plan is required (FREE, SILVER or GOLD)")
        SubscriptionPlanCode plan
) {
}
