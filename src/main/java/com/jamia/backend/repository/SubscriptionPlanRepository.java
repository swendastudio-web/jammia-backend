package com.jamia.backend.repository;

import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads subscription plans from the "subscription_plans" table.
 */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    // Used by registration: every new user starts on the FREE plan.
    Optional<SubscriptionPlan> findByCode(SubscriptionPlanCode code);

    // Used to show all plans, smallest first.
    List<SubscriptionPlan> findAllByOrderByMaxMembersPerRoomAsc();
}
