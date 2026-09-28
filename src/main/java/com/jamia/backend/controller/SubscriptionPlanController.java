package com.jamia.backend.controller;

import com.jamia.backend.dto.SubscriptionPlanResponse;
import com.jamia.backend.service.SubscriptionPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP endpoint to see the subscription plans and their room-size limits.
 */
@RestController
@RequestMapping("/api/subscription-plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    public SubscriptionPlanController(SubscriptionPlanService subscriptionPlanService) {
        this.subscriptionPlanService = subscriptionPlanService;
    }

    // GET /api/subscription-plans -> all plans, smallest first (login needed)
    @GetMapping
    public List<SubscriptionPlanResponse> getAllPlans() {
        return subscriptionPlanService.getAllPlans().stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }
}
