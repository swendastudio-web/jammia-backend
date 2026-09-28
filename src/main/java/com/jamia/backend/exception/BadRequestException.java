package com.jamia.backend.exception;

/**
 * Thrown when the request itself does not make sense, e.g. a turn order missing a member (-> 400).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
