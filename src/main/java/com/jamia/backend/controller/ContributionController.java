package com.jamia.backend.controller;

import com.jamia.backend.dto.ContributionResponse;
import com.jamia.backend.service.ContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP endpoints for payment tracking inside a room. All of them need a login token.
 * The rules live in ContributionService.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/contributions")
public class ContributionController {

    private final ContributionService contributionService;

    public ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    // GET /api/rooms/{roomId}/contributions           -> payments of the newest round
    // GET /api/rooms/{roomId}/contributions?round=1   -> payments of round 1 (history)
    @GetMapping
    public List<ContributionResponse> getContributions(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Long roomId,
                                                       @RequestParam(required = false) Integer round) {
        return contributionService.getContributions(roomId, currentUserId(jwt), round);
    }

    // POST /api/rooms/{roomId}/contributions/{id}/paid -> payer says "I paid"
    @PostMapping("/{contributionId}/paid")
    public ContributionResponse markPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                         @PathVariable Long contributionId) {
        return contributionService.markPaid(roomId, contributionId, currentUserId(jwt));
    }

    // POST /api/rooms/{roomId}/contributions/{id}/confirm -> recipient says "I received it"
    @PostMapping("/{contributionId}/confirm")
    public ContributionResponse confirmReceived(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roomId,
                                                @PathVariable Long contributionId) {
        return contributionService.confirmReceived(roomId, contributionId, currentUserId(jwt));
    }

    // The JWT "subject" is the user id we put in at login.
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
