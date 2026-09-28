package com.jamia.backend.exception;

/**
 * Thrown when a signed-in user types the wrong current password (e.g. when changing it).
 * This is 400, not 401: the user is signed in; only the typed password is wrong.
 */
public class IncorrectPasswordException extends RuntimeException {

    public IncorrectPasswordException() {
        super("Current password is incorrect");
    }
}
