package org.metrics.defectlab.auth.usecase.changepassword;

/**
 * Updates the password for an authenticated user account.
 */
public interface ChangePasswordUseCase {

    void changePassword(Long userId, String currentPassword, String newPassword);
}
