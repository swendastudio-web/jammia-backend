package com.jamia.backend.repository;

import com.jamia.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads and saves users in the "users" table.
 * Spring Data JPA creates the implementation automatically.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // Used by login: find the account that belongs to this email.
    Optional<User> findByEmail(String email);

    // Used by registration: stop two accounts from using the same email.
    boolean existsByEmail(String email);
}
