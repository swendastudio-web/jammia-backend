package com.jamia.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The data the app sends to register a new user.
 * The annotations check the data before it reaches the service.
 */
public record RegisterUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        // BCrypt only uses the first 72 bytes of a password, so we cap it there.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        // Optional. The app's language, e.g. "en", "ar", "fr", "ru", "am". Default "en".
        @Pattern(regexp = "^[a-z]{2,3}$", message = "Language must be a language code like en or ar")
        String preferredLanguage
) {

    // Runs when the request is created, before validation:
    // removes spaces around the email (phone keyboards often add one).
    public RegisterUserRequest {
        if (email != null) {
            email = email.trim();
        }
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            preferredLanguage = "en";
        }
    }
}
