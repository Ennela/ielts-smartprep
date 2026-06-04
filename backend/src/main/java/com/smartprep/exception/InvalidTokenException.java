package com.smartprep.exception;

/**
 * Thrown when a token (refresh, password reset, email verification) is invalid or expired.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
