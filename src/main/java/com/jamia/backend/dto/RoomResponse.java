package com.jamia.backend.dto;

import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A savings room as its members see it. Only members ever receive this.
 *
 * @param currentRound    the running round (null when the room is OPEN between rounds)
 * @param completedRounds how many rounds have finished (their history is at /rounds)
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
        Long creatorUserId,
        LocalDateTime createdAt,
        List<RoomMemberResponse> members,
        RoundResponse currentRound,
        long completedRounds
) {

    public static RoomResponse from(SavingsRoom room, List<RoomMember> activeMembers,
                                    RoundResponse currentRound, long completedRounds) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getContributionAmount(),
                room.getCurrency(),
                room.getFrequency(),
                room.getMaxMembers(),
                room.getStatus(),
                room.getCreator().getId(),
                room.getCreatedAt(),
                activeMembers.stream().map(RoomMemberResponse::from).toList(),
                currentRound,
                completedRounds
        );
    }
}
