package com.jamia.backend.dto;

import com.jamia.backend.entity.User;

import java.time.LocalDateTime;

/**
 * The user's own account data we send back to the app (only to that user).
 * It never includes the password hash.
 */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String subscriptionPlan,
        String role,
        boolean hasProfilePhoto,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getSubscriptionPlan() == null ? null : user.getSubscriptionPlan().getCode().name(),
                user.getRole().name(),
                user.getProfilePhotoFilename() != null,
                user.getCreatedAt()
        );
    }
}
