package com.jamia.backend.dto;

/**
 * Facts about this backend that the app needs to know.
 *
 * @param fiveMinuteCyclesEnabled true only on a development backend: the app may offer the 5-minute test period
 */
public record AppInfoResponse(boolean fiveMinuteCyclesEnabled) {
}
