package com.jamia.backend.dto;

/**
 * What login and refresh send back to the app.
 *
 * @param accessToken  JWT to send with every request: "Authorization: Bearer <accessToken>"
 * @param refreshToken used only to get a new pair of tokens when the access token expires
 * @param tokenType    always "Bearer"
 * @param expiresIn    seconds until the access token expires
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
