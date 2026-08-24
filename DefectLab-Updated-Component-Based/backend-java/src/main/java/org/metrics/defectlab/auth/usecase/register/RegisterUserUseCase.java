package org.metrics.defectlab.auth.usecase.register;

import org.metrics.defectlab.auth.domain.User;

/** Input boundary: creates a new account. */
public interface RegisterUserUseCase {

    User register(RegisterUserCommand command);
}
