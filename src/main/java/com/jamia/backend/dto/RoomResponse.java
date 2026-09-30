package com.jamia.backend.dto;

import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.TurnOrderMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A savings room as its members see it. Only members ever receive this.
 */
public record RoomResponse(
        Long id,
        String name,
        String description,
        BigDecimal contributionAmount,
        String currency,
        ContributionFrequency frequency,
        int maxMembers,
        RoomStatus status,
        TurnOrderMethod turnOrderMethod,
        LocalDate startDate,
        Long creatorUserId,
        LocalDateTime createdAt,
        List<RoomMemberResponse> members
) {

    public static RoomResponse from(SavingsRoom room, List<RoomMember> members) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getContributionAmount(),
                room.getCurrency(),
                room.getFrequency(),
                room.getMaxMembers(),
                room.getStatus(),
                room.getTurnOrderMethod(),
                room.getStartDate(),
                room.getCreator().getId(),
                room.getCreatedAt(),
                members.stream().map(RoomMemberResponse::from).toList()
        );
    }
}
