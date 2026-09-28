package com.jamia.backend.dto;

import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One contribution (who pays whom, for which cycle, and its status). Only room members receive this.
 */
public record ContributionResponse(
        Long id,
        int cycleNumber,
        LocalDate dueDate,
        RoomMemberResponse payer,
        RoomMemberResponse recipient,
        BigDecimal amount,
        ContributionStatus status,
        LocalDateTime paidAt,
        LocalDateTime confirmedAt
) {

    public static ContributionResponse from(Contribution contribution) {
        return new ContributionResponse(
                contribution.getId(),
                contribution.getCycleNumber(),
                contribution.getDueDate(),
                RoomMemberResponse.from(contribution.getPayer()),
                RoomMemberResponse.from(contribution.getRecipient()),
                contribution.getAmount(),
                contribution.getStatus(),
                contribution.getPaidAt(),
                contribution.getConfirmedAt()
        );
    }
}
