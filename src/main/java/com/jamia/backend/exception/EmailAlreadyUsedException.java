package com.jamia.backend.exception;

/**
 * Thrown when someone tries to register with an email that already has an account.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account with email " + email + " already exists");
    }
}
