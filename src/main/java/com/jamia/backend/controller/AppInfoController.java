package com.jamia.backend.controller;

import com.jamia.backend.dto.AppInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facts about this backend that the app needs (login needed).
 */
@RestController
public class AppInfoController {

    private final boolean fiveMinuteCyclesEnabled;

    public AppInfoController(@Value("${jamia.dev.five-minute-cycles}") boolean fiveMinuteCyclesEnabled) {
        this.fiveMinuteCyclesEnabled = fiveMinuteCyclesEnabled;
    }

    // GET /api/app-info -> e.g. whether the 5-minute test period may be offered
    @GetMapping("/api/app-info")
    public AppInfoResponse appInfo() {
        return new AppInfoResponse(fiveMinuteCyclesEnabled);
    }
}
