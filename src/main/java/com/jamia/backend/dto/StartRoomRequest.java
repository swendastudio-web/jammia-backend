package com.jamia.backend.dto;

import com.jamia.backend.entity.TurnOrderMethod;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * The data the admin sends to start a round.
 *
 * @param turnOrderMethod RANDOM (the app shuffles) or MANUAL (the admin decides)
 * @param startDate       the day turn 1 begins (today or later). Not needed for 5-minute test rooms,
 *                        which start at once. If it is today, the round starts now.
 * @param memberOrder     only for MANUAL: every member's user id, first receiver first
 */
public record StartRoomRequest(

        @NotNull(message = "Turn order method is required (RANDOM or MANUAL)")
        TurnOrderMethod turnOrderMethod,

        @FutureOrPresent(message = "Start date cannot be in the past")
        LocalDate startDate,

        List<Long> memberOrder
) {
}
