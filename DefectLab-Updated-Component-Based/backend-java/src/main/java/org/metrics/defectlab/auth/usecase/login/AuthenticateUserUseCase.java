package org.metrics.defectlab.auth.usecase.login;

import org.metrics.defectlab.auth.domain.User;

/** Input boundary: verifies credentials and signs a user in. */
public interface AuthenticateUserUseCase {

    User authenticate(String email, String password);
}
