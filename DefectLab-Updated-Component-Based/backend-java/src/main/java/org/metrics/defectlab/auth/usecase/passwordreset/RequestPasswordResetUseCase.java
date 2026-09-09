package org.metrics.defectlab.auth.usecase.passwordreset;

/** Input boundary: step 1 of the reset flow — confirms the address belongs to an account. */
public interface RequestPasswordResetUseCase {

    boolean isEmailRegistered(String email);
}
