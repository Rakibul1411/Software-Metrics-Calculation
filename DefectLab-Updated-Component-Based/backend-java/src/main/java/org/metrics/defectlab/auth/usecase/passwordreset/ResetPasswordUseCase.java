package org.metrics.defectlab.auth.usecase.passwordreset;

/** Input boundary: step 2 of the reset flow — sets the new password for that address. */
public interface ResetPasswordUseCase {

    void resetPassword(String email, String newPassword);
}
