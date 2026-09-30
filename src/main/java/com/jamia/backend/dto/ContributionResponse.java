package com.jamia.backend.dto;

import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One contribution (who pays whom, in which round and turn, and its status). Only room members receive this.
 *
 * @param dueAt      when this turn begins (the payment is due)
 * @param turnEndsAt when the turn moves on to the next person
 * @param late       the turn has passed and it is still not paid
 */
public record ContributionResponse(
        Long id,
        int roundNumber,
        int cycleNumber,
        LocalDateTime dueAt,
        LocalDateTime turnEndsAt,
        RoomMemberResponse payer,
        RoomMemberResponse recipient,
        BigDecimal amount,
        ContributionStatus status,
        boolean late,
        LocalDateTime paidAt,
        LocalDateTime confirmedAt
) {

    public static ContributionResponse from(Contribution c, LocalDateTime turnEndsAt, LocalDateTime now) {
        boolean late = c.getStatus() == ContributionStatus.PENDING && !now.isBefore(turnEndsAt);
        return new ContributionResponse(
                c.getId(),
                c.getRound().getRoundNumber(),
                c.getCycleNumber(),
                c.getDueAt(),
                turnEndsAt,
                RoomMemberResponse.from(c.getPayer()),
                RoomMemberResponse.from(c.getRecipient()),
                c.getAmount(),
                c.getStatus(),
                late,
                c.getPaidAt(),
                c.getConfirmedAt()
        );
    }
}
