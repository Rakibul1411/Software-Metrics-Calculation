package org.metrics.defectlab.auth.usecase.login;

import org.metrics.defectlab.auth.domain.User;

/**
 * Authenticates user credentials.
 */
public interface AuthenticateUserUseCase {

    User authenticate(String email, String password);
}
