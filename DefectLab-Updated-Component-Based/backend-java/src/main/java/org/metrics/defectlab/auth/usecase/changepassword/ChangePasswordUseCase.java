package org.metrics.defectlab.auth.usecase.changepassword;

/** Input boundary: changes the password for an already-authenticated account. */
public interface ChangePasswordUseCase {

    void changePassword(Long userId, String currentPassword, String newPassword);
}
