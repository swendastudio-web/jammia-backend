package com.jamia.backend.service;

import com.jamia.backend.dto.AuthResponse;
import com.jamia.backend.entity.RefreshToken;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.IncorrectPasswordException;
import com.jamia.backend.exception.InvalidCredentialsException;
import com.jamia.backend.exception.InvalidRefreshTokenException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.RefreshTokenRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Business rules for signing in: login, getting new tokens (refresh) and logout.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    // Creates unpredictable random values for refresh tokens.
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       @Value("${jamia.jwt.access-token-minutes}") long accessTokenMinutes,
                       @Value("${jamia.jwt.refresh-token-days}") long refreshTokenDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    // Checks email + password and returns a new pair of tokens.
    @Transactional
    public AuthResponse login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return createTokens(user);
    }

    // Swaps a valid refresh token for a new pair. The old refresh token stops working (rotation).
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (storedToken.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        // Each refresh token works only once: delete it before giving out a new one.
        refreshTokenRepository.delete(storedToken);

        return createTokens(storedToken.getUser());
    }

    // Signs this device out by deleting its refresh token. Does nothing if it is already gone.
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    // Changes the password, signs the user out of ALL devices, and returns new tokens for this device.
    @Transactional
    public AuthResponse changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IncorrectPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.deleteByUser(user);

        return createTokens(user);
    }

    private AuthResponse createTokens(User user) {
        String accessToken = createAccessToken(user);

        String refreshToken = createRandomToken();
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(refreshTokenDays);
        refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), refreshExpiresAt));

        return new AuthResponse(accessToken, refreshToken, "Bearer", accessTokenMinutes * 60);
    }

    // The JWT only contains the user id and times — no personal data, because anyone can read a JWT.
    private String createAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenMinutes, ChronoUnit.MINUTES))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // 32 random bytes, written as URL-safe text (about 43 characters).
    private String createRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 hash as 64 hex characters. This is what we store in the database.
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // Every Java installation includes SHA-256, so this should never happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
