package com.jamia.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Admin: new values for the JAMIA-wide settings.
 */
public record UpdateAppSettingsRequest(

        @NotNull(message = "Invite link validity (days) is required")
        @Min(value = 1, message = "Invite links must be valid for at least 1 day")
        @Max(value = 90, message = "Invite links can be valid for at most 90 days")
        Integer inviteLinkValidDays
) {
}
