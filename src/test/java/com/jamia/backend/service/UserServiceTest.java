package com.jamia.backend.service;

import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.entity.User;
import com.jamia.backend.entity.UserRole;
import com.jamia.backend.exception.EmailAlreadyUsedException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.SubscriptionPlanRepository;
import com.jamia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the user business rules. The repository is a mock (fake),
 * so these tests do not need a database.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SubscriptionPlan freePlan = new SubscriptionPlan(SubscriptionPlanCode.FREE, 5);

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, subscriptionPlanRepository, passwordEncoder);
    }

    @Test
    void registerUser_savesUserWithCleanedEmailAndHashedPasswordOnFreePlan() {
        when(userRepository.existsByEmail("ali@mail.com")).thenReturn(false);
        when(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.FREE)).thenReturn(Optional.of(freePlan));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User user = userService.registerUser(" Ali ", " Hassan ", " Ali@Mail.com ", "secret123", "ar");

        assertThat(user.getFirstName()).isEqualTo("Ali");
        assertThat(user.getLastName()).isEqualTo("Hassan");
        assertThat(user.getEmail()).isEqualTo("ali@mail.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();
        assertThat(user.getSubscriptionPlan()).isSameAs(freePlan);
        assertThat(user.getPreferredLanguage()).isEqualTo("ar");
    }

    @Test
    void registerUser_rejectsEmailThatIsAlreadyUsed() {
        when(userRepository.existsByEmail("ali@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("Ali", "Hassan", "ali@mail.com", "secret123", "en"))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerUser_rejectsDuplicateCaughtByDatabase() {
        when(userRepository.existsByEmail("ali@mail.com")).thenReturn(false);
        when(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.FREE)).thenReturn(Optional.of(freePlan));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.registerUser("Ali", "Hassan", "ali@mail.com", "secret123", "en"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void getUserById_throwsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateProfile_changesNameAndPhoneButNotEmail() {
        User user = new User("Ali", "Hassan", "ali@mail.com", "hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        User updated = userService.updateProfile(5L, " Ali ", " Saleh ", "+96891234567", null);

        assertThat(updated.getFirstName()).isEqualTo("Ali");
        assertThat(updated.getLastName()).isEqualTo("Saleh");
        assertThat(updated.getPhoneNumber()).isEqualTo("+96891234567");
        assertThat(updated.getEmail()).isEqualTo("ali@mail.com");
        assertThat(updated.getPreferredLanguage()).isEqualTo("en");   // null = keep current
    }

    @Test
    void updateProfile_changesTheLanguageWhenGiven() {
        User user = new User("Ali", "Hassan", "ali@mail.com", "hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        assertThat(userService.updateProfile(5L, "Ali", "Hassan", null, "fr").getPreferredLanguage()).isEqualTo("fr");
    }

    @Test
    void changeSubscriptionPlan_movesTheUserToTheNewPlan() {
        User user = new User("Ali", "Hassan", "ali@mail.com", "hash");
        user.setSubscriptionPlan(freePlan);
        SubscriptionPlan goldPlan = new SubscriptionPlan(SubscriptionPlanCode.GOLD, 100);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.GOLD)).thenReturn(Optional.of(goldPlan));

        User updated = userService.changeSubscriptionPlan(5L, SubscriptionPlanCode.GOLD);

        assertThat(updated.getSubscriptionPlan()).isSameAs(goldPlan);
    }

    @Test
    void newUsersAreNormalUsersNotAdmins() {
        assertThat(new User("Ali", "Hassan", "ali@mail.com", "hash").getRole()).isEqualTo(UserRole.USER);
    }
}
