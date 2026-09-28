package com.jamia.backend.dto;

import com.jamia.backend.entity.TurnOrderMethod;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * The data the creator sends to start the room.
 *
 * @param turnOrderMethod RANDOM (the app shuffles) or MANUAL (the creator decides)
 * @param startDate       due date of the first cycle (today or later)
 * @param memberOrder     only for MANUAL: every member's user id, first receiver first
 */
public record StartRoomRequest(

        @NotNull(message = "Turn order method is required (RANDOM or MANUAL)")
        TurnOrderMethod turnOrderMethod,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date cannot be in the past")
        LocalDate startDate,

        List<Long> memberOrder
) {
}
