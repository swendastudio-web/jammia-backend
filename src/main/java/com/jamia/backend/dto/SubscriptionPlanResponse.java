package com.jamia.backend.dto;

import com.jamia.backend.entity.SubscriptionPlan;

/**
 * A subscription plan as shown in the app.
 */
public record SubscriptionPlanResponse(
        String code,
        int maxMembersPerRoom
) {

    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(plan.getCode().name(), plan.getMaxMembersPerRoom());
    }
}
