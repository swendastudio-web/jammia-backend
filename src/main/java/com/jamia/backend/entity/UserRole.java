package com.jamia.backend.entity;

/**
 * What a user is allowed to do in JAMIA.
 */
public enum UserRole {
    // Every normal user.
    USER,
    // JAMIA staff: can change plan limits and users' plans.
    ADMIN
}
