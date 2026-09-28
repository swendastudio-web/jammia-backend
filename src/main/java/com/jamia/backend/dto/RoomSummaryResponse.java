package com.jamia.backend.dto;

import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;

import java.math.BigDecimal;

/**
 * A short version of a room for the "my rooms" list (no member list).
 */
public record RoomSummaryResponse(
        Long id,
        String name,
        BigDecimal contributionAmount,
        String currency,
        ContributionFrequency frequency,
        int maxMembers,
        RoomStatus status,
        boolean createdByMe
) {

    public static RoomSummaryResponse from(SavingsRoom room, Long currentUserId) {
        return new RoomSummaryResponse(
                room.getId(),
                room.getName(),
                room.getContributionAmount(),
                room.getCurrency(),
                room.getFrequency(),
                room.getMaxMembers(),
                room.getStatus(),
                room.isCreatedBy(currentUserId)
        );
    }
}
