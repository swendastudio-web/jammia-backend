package com.jamia.backend.dto;

import com.jamia.backend.entity.RoundStatus;
import com.jamia.backend.entity.TurnOrderMethod;

import java.time.LocalDateTime;

/**
 * One round (rotation) of a room, for the room screen and the rounds history.
 *
 * @param currentTurn            0 = not begun, 1..turnCount = running, turnCount + 1 = all turns passed
 * @param currentRecipientUserId who receives in the current turn (null if no turn is running)
 * @param currentTurnEndsAt      when the turn moves to the next person (null if no turn is running)
 */
public record RoundResponse(
        int roundNumber,
        RoundStatus status,
        TurnOrderMethod turnOrderMethod,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        LocalDateTime completedAt,
        int turnCount,
        int currentTurn,
        Long currentRecipientUserId,
        LocalDateTime currentTurnEndsAt,
        long paymentsTotal,
        long paymentsReceived
) {
}
