package com.jamia.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * The app's clock ("what time is it now?").
 * Services ask this Clock instead of calling LocalDateTime.now() directly,
 * so tests can use a fixed clock and "move time forward" without waiting.
 * @EnableScheduling turns on Spring's built-in timer, used by RoundClockJob.
 */
@Configuration
@EnableScheduling
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
