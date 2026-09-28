package com.jamia.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * The data the app sends to log in.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {

    // Removes spaces around the email before validation (phone keyboards often add one).
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
