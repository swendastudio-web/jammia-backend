package com.jamia.backend.service;

import com.jamia.backend.config.SecurityConfig;
import com.jamia.backend.dto.AuthResponse;
import com.jamia.backend.entity.RefreshToken;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.IncorrectPasswordException;
import com.jamia.backend.exception.InvalidCredentialsException;
import com.jamia.backend.exception.InvalidRefreshTokenException;
import com.jamia.backend.repository.RefreshTokenRepository;
import com.jamia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the sign-in rules. Repositories are mocks (fakes), so no database is needed.
 * The JWT encoder/decoder are the real ones from SecurityConfig.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-32-characters";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecurityConfig securityConfig = new SecurityConfig(TEST_SECRET);
    private final JwtDecoder jwtDecoder = securityConfig.jwtDecoder();

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                securityConfig.jwtEncoder(), 60, 90);

        user = new User("Ali", "Hassan", "ali@mail.com", passwordEncoder.encode("secret123"));
        // In real use the database gives the id; here we set it ourselves.
        ReflectionTestUtils.setField(user, "id", 5L);
    }

    @Test
    void login_returnsTokensAndStoresOnlyTheRefreshTokenHash() {
        when(userRepository.findByEmail("ali@mail.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(" Ali@Mail.com ", "secret123");

        // Access token: a valid JWT whose subject is the user id
        Jwt jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);

        // Refresh token: only its hash is saved, never the token itself
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(response.refreshToken());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now().plusDays(89));
    }

    @Test
    void login_rejectsWrongPassword() {
        when(userRepository.findByEmail("ali@mail.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("ali@mail.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_rejectsUnknownEmailWithTheSameError() {
        when(userRepository.findByEmail("nobody@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@mail.com", "secret123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void refresh_deletesOldTokenAndReturnsNewPair() {
        RefreshToken stored = new RefreshToken(user, "old-hash", LocalDateTime.now().plusDays(10));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        AuthResponse response = authService.refresh("old-refresh-token");

        verify(refreshTokenRepository).delete(stored);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        assertThat(jwtDecoder.decode(response.accessToken()).getSubject()).isEqualTo("5");
    }

    @Test
    void refresh_rejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_rejectsExpiredToken() {
        RefreshToken expired = new RefreshToken(user, "old-hash", LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_deletesTheToken() {
        RefreshToken stored = new RefreshToken(user, "some-hash", LocalDateTime.now().plusDays(10));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout("some-refresh-token");

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void logout_doesNothingWhenTokenIsAlreadyGone() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout("unknown-token");

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void changePassword_setsNewHashSignsOutAllDevicesAndReturnsNewTokens() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        AuthResponse response = authService.changePassword(5L, "secret123", "new-secret-456");

        assertThat(passwordEncoder.matches("new-secret-456", user.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).deleteByUser(user);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(jwtDecoder.decode(response.accessToken()).getSubject()).isEqualTo("5");
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(5L, "wrong", "new-secret-456"))
                .isInstanceOf(IncorrectPasswordException.class);
        verify(refreshTokenRepository, never()).deleteByUser(any());
        assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();
    }
}
