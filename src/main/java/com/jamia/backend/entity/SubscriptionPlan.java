package com.jamia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A subscription plan (Free, Silver, Gold) and the biggest room its users may create.
 * The limit is stored in the database so the admin can change it.
 */
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Saved as text ("FREE", "SILVER", "GOLD"), not as a number, so the database is readable.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private SubscriptionPlanCode code;

    @Column(name = "max_members_per_room", nullable = false)
    private int maxMembersPerRoom;

    // JPA needs an empty constructor to create objects from database rows.
    protected SubscriptionPlan() {
    }

    public SubscriptionPlan(SubscriptionPlanCode code, int maxMembersPerRoom) {
        this.code = code;
        this.maxMembersPerRoom = maxMembersPerRoom;
    }

    public Long getId() {
        return id;
    }

    public SubscriptionPlanCode getCode() {
        return code;
    }

    public int getMaxMembersPerRoom() {
        return maxMembersPerRoom;
    }

    public void setMaxMembersPerRoom(int maxMembersPerRoom) {
        this.maxMembersPerRoom = maxMembersPerRoom;
    }
}
