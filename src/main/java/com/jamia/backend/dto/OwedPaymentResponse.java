package com.jamia.backend.dto;

import com.jamia.backend.entity.Contribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment the user still has to make (its turn has started). Used for the "You owe" reminder,
 * also for rooms the user was removed from.
 *
 * @param stillMember false if the user was removed from (or left) this room
 */
public record OwedPaymentResponse(
        Long contributionId,
        Long roomId,
        String roomName,
        String currency,
        int roundNumber,
        int cycleNumber,
        LocalDateTime dueAt,
        String recipientName,
        BigDecimal amount,
        boolean late,
        boolean stillMember
) {

    public static OwedPaymentResponse from(Contribution c, LocalDateTime turnEndsAt, LocalDateTime now) {
        return new OwedPaymentResponse(
                c.getId(),
                c.getRoom().getId(),
                c.getRoom().getName(),
                c.getRoom().getCurrency(),
                c.getRound().getRoundNumber(),
                c.getCycleNumber(),
                c.getDueAt(),
                c.getRecipient().getUser().getFirstName() + " " + c.getRecipient().getUser().getLastName(),
                c.getAmount(),
                !now.isBefore(turnEndsAt),
                c.getPayer().isActive()
        );
    }
}
