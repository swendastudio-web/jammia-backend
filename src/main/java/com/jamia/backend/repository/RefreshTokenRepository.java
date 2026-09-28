package com.jamia.backend.repository;

import com.jamia.backend.entity.RefreshToken;
import com.jamia.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads, saves and deletes refresh tokens in the "refresh_tokens" table.
 * Spring Data JPA creates the implementation automatically.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Used by refresh and logout: find the token row by the hash of the token the app sent.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Used by password change: sign the user out of every device.
    void deleteByUser(User user);
}
