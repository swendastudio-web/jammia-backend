package com.jamia.backend.dto;

import com.jamia.backend.entity.RoomMember;

/**
 * A room member as other members see them: name and turn only
 * (no email or phone, to protect privacy).
 */
public record RoomMemberResponse(
        Long userId,
        String firstName,
        String lastName,
        Integer turnPosition
) {

    public static RoomMemberResponse from(RoomMember member) {
        return new RoomMemberResponse(
                member.getUser().getId(),
                member.getUser().getFirstName(),
                member.getUser().getLastName(),
                member.getTurnPosition()
        );
    }
}
