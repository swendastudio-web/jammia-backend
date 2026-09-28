package com.jamia.backend.exception;

/**
 * Thrown when a refresh token is unknown, already used, or expired.
 * The app should send the user back to the login screen.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
