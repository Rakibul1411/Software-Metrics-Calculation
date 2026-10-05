package org.metrics.defectlab.auth.usecase.passwordreset;

/**
 * Initiates a password reset request for a user email.
 */
public interface RequestPasswordResetUseCase {

    boolean isEmailRegistered(String email);
}
