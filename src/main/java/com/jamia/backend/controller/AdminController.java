package com.jamia.backend.controller;

import com.jamia.backend.dto.AppSettingsResponse;
import com.jamia.backend.dto.ChangeUserPlanRequest;
import com.jamia.backend.dto.SubscriptionPlanResponse;
import com.jamia.backend.dto.UpdateAppSettingsRequest;
import com.jamia.backend.dto.UpdatePlanLimitRequest;
import com.jamia.backend.dto.UserResponse;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.service.AppSettingsService;
import com.jamia.backend.service.SubscriptionPlanService;
import com.jamia.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for JAMIA admins only. SecurityConfig blocks everyone without the ADMIN role (403).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SubscriptionPlanService subscriptionPlanService;
    private final UserService userService;
    private final AppSettingsService appSettingsService;

    public AdminController(SubscriptionPlanService subscriptionPlanService, UserService userService,
                           AppSettingsService appSettingsService) {
        this.subscriptionPlanService = subscriptionPlanService;
        this.userService = userService;
        this.appSettingsService = appSettingsService;
    }

    // PUT /api/admin/subscription-plans/{code} -> change a plan's biggest room size
    @PutMapping("/subscription-plans/{code}")
    public SubscriptionPlanResponse updatePlanLimit(@PathVariable SubscriptionPlanCode code,
                                                    @Valid @RequestBody UpdatePlanLimitRequest request) {
        return SubscriptionPlanResponse.from(
                subscriptionPlanService.updateMaxMembersPerRoom(code, request.maxMembersPerRoom()));
    }

    // PUT /api/admin/users/{userId}/subscription-plan -> move a user to FREE, SILVER or GOLD
    @PutMapping("/users/{userId}/subscription-plan")
    public UserResponse changeUserPlan(@PathVariable Long userId,
                                       @Valid @RequestBody ChangeUserPlanRequest request) {
        return UserResponse.from(userService.changeSubscriptionPlan(userId, request.plan()));
    }

    // GET /api/admin/settings -> current JAMIA-wide settings
    @GetMapping("/settings")
    public AppSettingsResponse getSettings() {
        return AppSettingsResponse.from(appSettingsService.getSettings());
    }

    // PUT /api/admin/settings -> change settings (e.g. how many days new invite links work)
    @PutMapping("/settings")
    public AppSettingsResponse updateSettings(@Valid @RequestBody UpdateAppSettingsRequest request) {
        return AppSettingsResponse.from(appSettingsService.updateInviteLinkValidDays(request.inviteLinkValidDays()));
    }
}
