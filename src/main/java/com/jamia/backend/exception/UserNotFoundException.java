package com.jamia.backend.exception;

/**
 * Thrown when a user with the requested id does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("User with id " + id + " was not found");
    }
}
