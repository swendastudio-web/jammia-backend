package com.jamia.backend.controller;

import com.jamia.backend.dto.OwedPaymentResponse;
import com.jamia.backend.service.ContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The "You owe" reminder: payments the signed-in user still has to make, in every room.
 */
@RestController
public class OwedPaymentsController {

    private final ContributionService contributionService;

    public OwedPaymentsController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    // GET /api/users/me/owed-payments -> unpaid payments whose turn has started (oldest first)
    @GetMapping("/api/users/me/owed-payments")
    public List<OwedPaymentResponse> getOwedPayments(@AuthenticationPrincipal Jwt jwt) {
        return contributionService.getOwedPayments(Long.valueOf(jwt.getSubject()));
    }
}
