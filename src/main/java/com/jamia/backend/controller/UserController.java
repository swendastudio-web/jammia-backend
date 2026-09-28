package com.jamia.backend.controller;

import com.jamia.backend.dto.AuthResponse;
import com.jamia.backend.dto.ChangePasswordRequest;
import com.jamia.backend.dto.RegisterUserRequest;
import com.jamia.backend.dto.UpdateProfileRequest;
import com.jamia.backend.dto.UserResponse;
import com.jamia.backend.entity.User;
import com.jamia.backend.service.AuthService;
import com.jamia.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for users. It only receives requests and returns responses;
 * the business rules live in UserService.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    // POST /api/users -> register a new user, returns 201 Created
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        User user = userService.registerUser(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.password()
        );
        return UserResponse.from(user);
    }

    // GET /api/users/me -> the signed-in user's own account.
    // Spring has already checked the JWT; its "subject" is the user id we put in at login.
    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getUserById(currentUserId(jwt)));
    }

    // PUT /api/users/me -> update your own name and phone number
    @PutMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(
                currentUserId(jwt),
                request.firstName(),
                request.lastName(),
                request.phoneNumber()
        );
        return UserResponse.from(user);
    }

    // PUT /api/users/me/password -> change your password.
    // All devices are signed out; the response has new tokens for this device.
    @PutMapping("/me/password")
    public AuthResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(currentUserId(jwt), request.currentPassword(), request.newPassword());
    }

    // The JWT "subject" is the user id we put in at login.
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
