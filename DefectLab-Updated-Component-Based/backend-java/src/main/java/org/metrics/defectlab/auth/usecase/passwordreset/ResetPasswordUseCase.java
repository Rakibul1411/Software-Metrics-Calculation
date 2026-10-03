package org.metrics.defectlab.auth.usecase.passwordreset;

/**
 * Completes the password reset process with a new password.
 */
public interface ResetPasswordUseCase {

    void resetPassword(String email, String newPassword);
}
