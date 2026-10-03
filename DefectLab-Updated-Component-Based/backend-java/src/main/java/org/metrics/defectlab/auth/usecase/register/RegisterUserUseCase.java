package org.metrics.defectlab.auth.usecase.register;

import org.metrics.defectlab.auth.domain.User;

/**
 * Registers a new user account.
 */
public interface RegisterUserUseCase {

    User register(RegisterUserCommand command);
}
