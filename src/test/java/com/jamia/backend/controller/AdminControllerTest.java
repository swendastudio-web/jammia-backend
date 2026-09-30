package com.jamia.backend.controller;

import com.jamia.backend.config.SecurityConfig;
import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.entity.User;
import com.jamia.backend.service.AppSettingsService;
import com.jamia.backend.service.SubscriptionPlanService;
import com.jamia.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that /api/admin endpoints work for admins and are blocked for everyone else.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private SubscriptionPlanService subscriptionPlanService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AppSettingsService appSettingsService;

    @Test
    void admin_canChangeAPlanLimit() throws Exception {
        when(subscriptionPlanService.updateMaxMembersPerRoom(SubscriptionPlanCode.FREE, 8))
                .thenReturn(new SubscriptionPlan(SubscriptionPlanCode.FREE, 8));

        mockMvc.perform(put("/api/admin/subscription-plans/FREE")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMembersPerRoom\": 8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FREE"))
                .andExpect(jsonPath("$.maxMembersPerRoom").value(8));
    }

    @Test
    void admin_canMoveAUserToAnotherPlan() throws Exception {
        User user = new User("Ali", "Hassan", "ali@mail.com", "hash");
        user.setSubscriptionPlan(new SubscriptionPlan(SubscriptionPlanCode.GOLD, 100));
        when(userService.changeSubscriptionPlan(7L, SubscriptionPlanCode.GOLD)).thenReturn(user);

        mockMvc.perform(put("/api/admin/users/7/subscription-plan")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\": \"GOLD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionPlan").value("GOLD"));
    }

    @Test
    void normalUser_isForbidden() throws Exception {
        mockMvc.perform(put("/api/admin/subscription-plans/FREE")
                        .header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMembersPerRoom\": 100}"))
                .andExpect(status().isForbidden());

        verify(subscriptionPlanService, never()).updateMaxMembersPerRoom(any(), anyInt());
    }

    @Test
    void tokenWithoutAnyRole_isForbidden() throws Exception {
        mockMvc.perform(put("/api/admin/users/7/subscription-plan")
                        .header("Authorization", bearer(null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\": \"GOLD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(put("/api/admin/subscription-plans/FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMembersPerRoom\": 8}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_invalidValuesGive400() throws Exception {
        mockMvc.perform(put("/api/admin/subscription-plans/FREE")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMembersPerRoom\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxMembersPerRoom").exists());

        mockMvc.perform(put("/api/admin/subscription-plans/PLATINUM")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxMembersPerRoom\": 8}"))
                .andExpect(status().isBadRequest());
    }

    // A valid login token for user 1 with the given role (or no role at all)
    private String bearer(String role) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        if (role != null) {
            claims.claim("roles", List.of(role));
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
