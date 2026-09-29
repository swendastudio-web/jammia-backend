package com.jamia.backend.service;

import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.EmailAlreadyUsedException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.SubscriptionPlanRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for users: registering and finding user accounts.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;

    // Spring passes in the repositories and the password encoder automatically.
    public UserService(UserRepository userRepository,
                       SubscriptionPlanRepository subscriptionPlanRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(String firstName, String lastName, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = new User(firstName.trim(), lastName.trim(), normalizedEmail, passwordHash);

        // Every new user starts on the FREE plan.
        user.setSubscriptionPlan(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.FREE)
                .orElseThrow(() -> new IllegalStateException("FREE subscription plan is missing in the database")));

        try {
            // saveAndFlush writes to the database now, so the unique-email rule is checked here.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two people registered the same email at the same moment.
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
    }

    // Updates the editable profile fields. Email cannot be changed (it is the login ID).
    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String phoneNumber) {
        User user = getUserById(userId);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhoneNumber(phoneNumber);
        // No save() needed: JPA writes changes to a loaded entity when the transaction ends.
        return user;
    }

    // Admin only (checked in SecurityConfig): move a user to FREE, SILVER or GOLD.
    // JAMIA does not take payments; the admin does this after the user has paid outside the app.
    @Transactional
    public User changeSubscriptionPlan(Long userId, SubscriptionPlanCode code) {
        User user = getUserById(userId);
        user.setSubscriptionPlan(subscriptionPlanRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan " + code + " not found")));
        return user;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
