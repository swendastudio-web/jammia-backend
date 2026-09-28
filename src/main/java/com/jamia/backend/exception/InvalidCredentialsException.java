package com.jamia.backend.exception;

/**
 * Thrown when login fails. The message is the same for a wrong email and a wrong password,
 * so nobody can use login to find out which emails have an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
