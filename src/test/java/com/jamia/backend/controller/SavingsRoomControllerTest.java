package com.jamia.backend.controller;

import com.jamia.backend.config.SecurityConfig;
import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.service.SavingsRoomService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the /api/rooms endpoints with our real security rules. SavingsRoomService is a mock.
 */
@WebMvcTest(SavingsRoomController.class)
@Import(SecurityConfig.class)
class SavingsRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private SavingsRoomService roomService;

    private static final String VALID_ROOM = """
            {"name": "Family", "contributionAmount": 100.50, "currency": " aed ",
             "frequency": "MONTHLY", "maxMembers": 5}
            """;

    @Test
    void createRoom_returns201AndPassesCleanedValues() throws Exception {
        RoomResponse created = new RoomResponse(10L, "Family", null, new BigDecimal("100.50"), "AED",
                ContributionFrequency.MONTHLY, 5, RoomStatus.OPEN, 1L, null, List.of(), null, 0);
        when(roomService.createRoom(eq(1L), eq("Family"), isNull(), eq(new BigDecimal("100.50")), eq("AED"),
                eq(ContributionFrequency.MONTHLY), eq(5))).thenReturn(created);

        mockMvc.perform(post("/api/rooms").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ROOM))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createRoom_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/rooms").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "contributionAmount": 0, "currency": "dirham", "maxMembers": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.contributionAmount").exists())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.frequency").exists())
                .andExpect(jsonPath("$.errors.maxMembers").exists());
    }

    @Test
    void createRoom_returns400InTheSameFormatForAnUnknownFrequency() throws Exception {
        mockMvc.perform(post("/api/rooms").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "x", "contributionAmount": 10, "currency": "AED",
                                 "frequency": "YEARLY", "maxMembers": 2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void createRoom_returns409WhenThePlanLimitIsExceeded() throws Exception {
        when(roomService.createRoom(eq(1L), anyString(), any(), any(), anyString(), any(), anyInt()))
                .thenThrow(new BusinessRuleException("Your FREE plan allows rooms of up to 5 members"));

        mockMvc.perform(post("/api/rooms").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ROOM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Your FREE plan allows rooms of up to 5 members"));
    }

    @Test
    void rooms_needALoginToken() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
    }

    // A valid login token for user id 1
    private String bearer() {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
