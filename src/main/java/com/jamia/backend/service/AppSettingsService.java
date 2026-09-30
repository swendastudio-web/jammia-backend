package com.jamia.backend.service;

import com.jamia.backend.entity.AppSettings;
import com.jamia.backend.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and changes JAMIA-wide settings (the single row in "app_settings").
 */
@Service
public class AppSettingsService {

    private final AppSettingsRepository appSettingsRepository;

    public AppSettingsService(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @Transactional(readOnly = true)
    public AppSettings getSettings() {
        return appSettingsRepository.findById(AppSettings.ID)
                .orElseThrow(() -> new IllegalStateException("app_settings row is missing in the database"));
    }

    // Admin only (checked in SecurityConfig). Applies to invite links created from now on.
    @Transactional
    public AppSettings updateInviteLinkValidDays(int days) {
        AppSettings settings = getSettings();
        settings.setInviteLinkValidDays(days);
        return settings;
    }
}
