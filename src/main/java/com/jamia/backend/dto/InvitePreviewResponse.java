package com.jamia.backend.dto;

import com.jamia.backend.entity.ContributionFrequency;

import java.math.BigDecimal;

/**
 * What someone sees after opening an invite link, before asking to join.
 * Only room-level numbers and the referrer's name — never the member list.
 */
public record InvitePreviewResponse(
        String roomName,
        String roomDescription,
        BigDecimal contributionAmount,
        String currency,
        ContributionFrequency frequency,
        long memberCount,
        int maxMembers,
        String creatorName,
        String referredByName,
        boolean alreadyMember,
        boolean requestPending
) {
}
