package com.smartprep.exception;

/**
 * Thrown when a suspended account tries to log in or refresh its session.
 * Not to be confused with {@link AccountLockedException}, the temporary lockout after
 * too many failed passwords.
 */
public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException() {
        super("This account has been suspended. Contact an administrator to restore access.");
    }
}
