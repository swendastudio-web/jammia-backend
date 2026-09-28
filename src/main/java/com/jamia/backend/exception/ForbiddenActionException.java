package com.jamia.backend.exception;

/**
 * Thrown when a user may see something but is not allowed to do this action (-> 403).
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
