package com.jamia.backend.controller;

import com.jamia.backend.dto.InviteLinkResponse;
import com.jamia.backend.dto.InvitePreviewResponse;
import com.jamia.backend.dto.JoinRequestResponse;
import com.jamia.backend.service.InvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP endpoints for invitations. All of them need a login token.
 * The rules live in InvitationService.
 */
@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    // POST /api/rooms/{roomId}/invite-link -> your personal link to share (any member)
    // POST, not GET: it may create a new link on the server.
    @PostMapping("/api/rooms/{roomId}/invite-link")
    public InviteLinkResponse getMyInviteLink(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId) {
        return invitationService.getOrCreateMyInviteLink(roomId, currentUserId(jwt));
    }

    // GET /api/invites/{token} -> what the room is and who referred you, before asking to join
    @GetMapping("/api/invites/{token}")
    public InvitePreviewResponse previewInvite(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return invitationService.previewInvite(token, currentUserId(jwt));
    }

    // POST /api/invites/{token}/join-requests -> ask to join -> 201
    @PostMapping("/api/invites/{token}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRequestResponse requestToJoin(@AuthenticationPrincipal Jwt jwt, @PathVariable String token) {
        return invitationService.requestToJoin(token, currentUserId(jwt));
    }

    // GET /api/rooms/{roomId}/join-requests -> waiting requests (room creator only)
    @GetMapping("/api/rooms/{roomId}/join-requests")
    public List<JoinRequestResponse> getPendingRequests(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId) {
        return invitationService.getPendingRequests(roomId, currentUserId(jwt));
    }

    // POST /api/rooms/{roomId}/join-requests/{requestId}/approve -> accept (room creator only)
    @PostMapping("/api/rooms/{roomId}/join-requests/{requestId}/approve")
    public JoinRequestResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                       @PathVariable Long requestId) {
        return invitationService.approve(roomId, requestId, currentUserId(jwt));
    }

    // POST /api/rooms/{roomId}/join-requests/{requestId}/reject -> decline (room creator only)
    @PostMapping("/api/rooms/{roomId}/join-requests/{requestId}/reject")
    public JoinRequestResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                      @PathVariable Long requestId) {
        return invitationService.reject(roomId, requestId, currentUserId(jwt));
    }

    // The JWT "subject" is the user id we put in at login.
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
