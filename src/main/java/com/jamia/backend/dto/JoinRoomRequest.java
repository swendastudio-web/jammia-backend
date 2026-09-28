package com.jamia.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The data the app sends to join a room with the code the creator shared.
 */
public record JoinRoomRequest(

        @NotBlank(message = "Join code is required")
        String joinCode
) {

    // Codes are not case-sensitive and spaces around them are ignored.
    public JoinRoomRequest {
        if (joinCode != null) {
            joinCode = joinCode.trim().toUpperCase();
        }
    }
}
