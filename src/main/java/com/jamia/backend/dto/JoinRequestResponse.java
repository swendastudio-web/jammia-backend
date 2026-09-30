package com.jamia.backend.dto;

import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;

import java.time.LocalDateTime;

/**
 * A join request as the room creator sees it: WHO is asking and WHICH member referred them.
 */
public record JoinRequestResponse(
        Long id,
        Long requesterUserId,
        String requesterFirstName,
        String requesterLastName,
        Long referredByUserId,
        String referredByFirstName,
        String referredByLastName,
        JoinRequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {

    public static JoinRequestResponse from(JoinRequest request) {
        return new JoinRequestResponse(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getFirstName(),
                request.getUser().getLastName(),
                request.getReferredBy().getUser().getId(),
                request.getReferredBy().getUser().getFirstName(),
                request.getReferredBy().getUser().getLastName(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getDecidedAt()
        );
    }
}
