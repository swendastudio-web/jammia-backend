package com.jamia.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The profile fields a user can change. Email is not here: it cannot be changed.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        // Optional. International format: "+" then 8-15 digits, e.g. +96891234567
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "Phone number must be in international format, e.g. +96891234567")
        String phoneNumber
) {

    // Before validation: remove spaces around the phone, and treat an empty phone as "no phone".
    public UpdateProfileRequest {
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
            if (phoneNumber.isEmpty()) {
                phoneNumber = null;
            }
        }
    }
}
