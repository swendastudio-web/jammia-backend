package com.jamia.backend.service;

import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads subscription plans, and lets the admin change a plan's room-size limit.
 */
@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionPlanService(SubscriptionPlanRepository subscriptionPlanRepository) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findAllByOrderByMaxMembersPerRoomAsc();
    }

    // Admin only (checked in SecurityConfig). Rooms that already exist keep their own max members;
    // the new limit applies to rooms created from now on.
    @Transactional
    public SubscriptionPlan updateMaxMembersPerRoom(SubscriptionPlanCode code, int maxMembersPerRoom) {
        SubscriptionPlan plan = findPlan(code);
        plan.setMaxMembersPerRoom(maxMembersPerRoom);
        return plan;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan findPlan(SubscriptionPlanCode code) {
        return subscriptionPlanRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan " + code + " not found"));
    }
}
