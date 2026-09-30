package com.jamia.backend.dto;

import java.time.LocalDateTime;

/**
 * A member's invite link, ready to share (WhatsApp, Facebook, Instagram, SMS...).
 *
 * @param token     the secret part of the link
 * @param url       the full link to share
 * @param expiresAt after this moment the link no longer works
 */
public record InviteLinkResponse(
        String token,
        String url,
        LocalDateTime expiresAt
) {
}
