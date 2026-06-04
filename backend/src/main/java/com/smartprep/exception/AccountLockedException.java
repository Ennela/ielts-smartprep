package com.smartprep.exception;

import lombok.Getter;

/**
 * Thrown when a user account is temporarily locked due to too many failed login attempts.
 */
@Getter
public class AccountLockedException extends RuntimeException {

    private final long remainingSeconds;
    private final int maxAttempts;

    public AccountLockedException(long remainingSeconds, int maxAttempts) {
        super("Account temporarily locked after " + maxAttempts + " failed attempts. Try again in "
                + remainingSeconds + " seconds.");
        this.remainingSeconds = remainingSeconds;
        this.maxAttempts = maxAttempts;
    }
}
