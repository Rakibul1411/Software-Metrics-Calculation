package org.metrics.defectlab.auth.usecase;

/** Application Business Rule: the password strength policy this app enforces. */
public final class PasswordPolicy {

    private static final int MINIMUM_LENGTH = 8;
    private static final int MAXIMUM_LENGTH = 12;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MINIMUM_LENGTH) {
            throw new IllegalArgumentException(
                    "The password must be at least " + MINIMUM_LENGTH + " characters.");
        }
        if (password.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException(
                    "The password must not exceed " + MAXIMUM_LENGTH + " characters.");
        }
    }
}
