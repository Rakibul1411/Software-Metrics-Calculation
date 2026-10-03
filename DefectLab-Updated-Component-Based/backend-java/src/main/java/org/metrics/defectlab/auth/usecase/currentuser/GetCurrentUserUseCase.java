package org.metrics.defectlab.auth.usecase.currentuser;

import org.metrics.defectlab.auth.domain.User;

/**
 * Resolves the currently authenticated user account.
 */
public interface GetCurrentUserUseCase {

    User getById(Long userId);
}
