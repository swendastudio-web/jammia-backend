package com.jamia.backend.exception;

/**
 * Thrown when the current state does not allow the action, e.g. the room is full (-> 409).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
