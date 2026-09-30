package com.jamia.backend.dto;

import com.jamia.backend.entity.AppSettings;

/**
 * JAMIA-wide settings as the admin sees them.
 */
public record AppSettingsResponse(
        int inviteLinkValidDays
) {

    public static AppSettingsResponse from(AppSettings settings) {
        return new AppSettingsResponse(settings.getInviteLinkValidDays());
    }
}
