package com.jamia.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The data the app sends to refresh its tokens or to log out.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
