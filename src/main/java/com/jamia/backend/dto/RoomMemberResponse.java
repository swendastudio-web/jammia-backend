package com.jamia.backend.dto;

import com.jamia.backend.entity.RoomMember;

/**
 * A room member as other members see them: name, turn, and whether they have a photo
 * (no email or phone, to protect privacy). The photo itself: GET /api/users/{userId}/photo
 */
public record RoomMemberResponse(
        Long userId,
        String firstName,
        String lastName,
        Integer turnPosition,
        boolean hasProfilePhoto
) {

    public static RoomMemberResponse from(RoomMember member) {
        return new RoomMemberResponse(
                member.getUser().getId(),
                member.getUser().getFirstName(),
                member.getUser().getLastName(),
                member.getTurnPosition(),
                member.getUser().getProfilePhotoFilename() != null
        );
    }
}
