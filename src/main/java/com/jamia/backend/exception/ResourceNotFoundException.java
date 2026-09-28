package com.jamia.backend.exception;

/**
 * Thrown when something does not exist, or the user is not allowed to know it exists (-> 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
