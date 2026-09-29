package com.jamia.backend.controller;

import com.jamia.backend.config.SecurityConfig;
import com.jamia.backend.dto.ProfilePhoto;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.service.ProfilePhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the photo endpoints with our real security rules. ProfilePhotoService is a mock.
 */
@WebMvcTest(ProfilePhotoController.class)
@Import(SecurityConfig.class)
class ProfilePhotoControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private ProfilePhotoService photoService;

    @Test
    void upload_returns204AndPassesTheFileBytes() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo")
                        .file(new MockMultipartFile("photo", "me.png", "image/png", PNG))
                        .header("Authorization", bearer("1")))
                .andExpect(status().isNoContent());

        verify(photoService).uploadPhoto(1L, PNG);
    }

    @Test
    void upload_returns400ForANonImage() throws Exception {
        doThrow(new BadRequestException("Only JPEG and PNG photos are allowed"))
                .when(photoService).uploadPhoto(eq(1L), any());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo")
                        .file(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", "text".getBytes()))
                        .header("Authorization", bearer("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only JPEG and PNG photos are allowed"));
    }

    @Test
    void upload_returns400WhenThePhotoFieldIsMissing() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo")
                        .file(new MockMultipartFile("picture", "me.png", "image/png", PNG))
                        .header("Authorization", bearer("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Missing file 'photo'"));
    }

    @Test
    void upload_needsALoginToken() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/photo")
                        .file(new MockMultipartFile("photo", "me.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/me/photo").header("Authorization", bearer("1")))
                .andExpect(status().isNoContent());

        verify(photoService).deletePhoto(1L);
    }

    @Test
    void get_returnsTheImageWithItsTypeAndPrivateCaching() throws Exception {
        when(photoService.getPhoto(2L, 1L)).thenReturn(new ProfilePhoto(PNG, "image/png"));

        mockMvc.perform(get("/api/users/1/photo").header("Authorization", bearer("2")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "max-age=300, private"));
    }

    @Test
    void get_returns404ForStrangers() throws Exception {
        when(photoService.getPhoto(3L, 1L)).thenThrow(new ResourceNotFoundException("Photo not found"));

        mockMvc.perform(get("/api/users/1/photo").header("Authorization", bearer("3")))
                .andExpect(status().isNotFound());
    }

    // A valid login token for the given user id
    private String bearer(String userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
