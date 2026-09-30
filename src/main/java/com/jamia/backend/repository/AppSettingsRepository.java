package com.jamia.backend.repository;

import com.jamia.backend.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and saves the single row of JAMIA-wide settings (id = AppSettings.ID).
 */
public interface AppSettingsRepository extends JpaRepository<AppSettings, Short> {
}
