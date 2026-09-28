package com.jamia.backend.controller;

import com.jamia.backend.config.SecurityConfig;
import com.jamia.backend.dto.AuthResponse;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.EmailAlreadyUsedException;
import com.jamia.backend.exception.IncorrectPasswordException;
import com.jamia.backend.service.AuthService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer with our real security rules: status codes, validation and response body.
 * UserService is replaced with a mock.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real token encoder from SecurityConfig, used to create test login tokens.
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    private static final String VALID_REQUEST = """
            {
              "firstName": "Ali",
              "lastName": "Hassan",
              "email": "ali@mail.com",
              "password": "secret123"
            }
            """;

    @Test
    void registerUser_returns201AndNeverReturnsPassword() throws Exception {
        User saved = new User("Ali", "Hassan", "ali@mail.com", "hashed-value");
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString())).thenReturn(saved);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.email").value("ali@mail.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerUser_returns400WhenDataIsInvalid() throws Exception {
        String invalidRequest = """
                {
                  "firstName": "",
                  "lastName": "Hassan",
                  "email": "not-an-email",
                  "password": "short"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerUser_acceptsEmailWithSpacesAround() throws Exception {
        User saved = new User("Ali", "Hassan", "ali@mail.com", "hashed-value");
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString())).thenReturn(saved);

        String requestWithSpaces = """
                {
                  "firstName": "Ali",
                  "lastName": "Hassan",
                  "email": "  ali@mail.com ",
                  "password": "secret123"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithSpaces))
                .andExpect(status().isCreated());
    }

    @Test
    void registerUser_returns409WhenEmailIsAlreadyUsed() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new EmailAlreadyUsedException("ali@mail.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with email ali@mail.com already exists"));
    }

    @Test
    void me_returnsTheSignedInUsersOwnAccount() throws Exception {
        User user = new User("Ali", "Hassan", "ali@mail.com", "hashed-value");
        ReflectionTestUtils.setField(user, "id", 5L);
        when(userService.getUserById(5L)).thenReturn(user);

        String token = createToken(jwtEncoder, "5", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("ali@mail.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns401WithExpiredToken() throws Exception {
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        String expiredToken = createToken(jwtEncoder, "5", twoHoursAgo, oneHourAgo);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns401WithTokenSignedByAnotherKey() throws Exception {
        // A fake token made by someone who does not know our secret key
        JwtEncoder attackerEncoder = new SecurityConfig("attacker-secret-key-that-is-32-characters-long").jwtEncoder();
        String forgedToken = createToken(attackerEncoder, "5", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_savesNameAndPhone() throws Exception {
        User updated = new User("Ali", "Saleh", "ali@mail.com", "hashed-value");
        updated.setPhoneNumber("+96891234567");
        when(userService.updateProfile(5L, "Ali", "Saleh", "+96891234567")).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ali", "lastName": "Saleh", "phoneNumber": " +96891234567 "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Saleh"))
                .andExpect(jsonPath("$.phoneNumber").value("+96891234567"));
    }

    @Test
    void updateProfile_treatsEmptyPhoneAsNoPhone() throws Exception {
        User updated = new User("Ali", "Hassan", "ali@mail.com", "hashed-value");
        when(userService.updateProfile(eq(5L), eq("Ali"), eq("Hassan"), isNull())).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ali", "lastName": "Hassan", "phoneNumber": ""}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_returns400ForBadPhoneFormat() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ali", "lastName": "Hassan", "phoneNumber": "0501234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").exists());
    }

    @Test
    void updateProfile_returns401WithoutToken() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Ali", "lastName": "Hassan"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returnsNewTokens() throws Exception {
        when(authService.changePassword(5L, "secret123", "new-secret-456"))
                .thenReturn(new AuthResponse("new-access", "new-refresh", "Bearer", 3600));

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "secret123", "newPassword": "new-secret-456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void changePassword_returns400WhenCurrentPasswordIsWrong() throws Exception {
        when(authService.changePassword(5L, "wrong", "new-secret-456"))
                .thenThrow(new IncorrectPasswordException());

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong", "newPassword": "new-secret-456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    // A valid token for user id 5
    private String validToken() {
        return createToken(jwtEncoder, "5", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private static String createToken(JwtEncoder encoder, String userId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
