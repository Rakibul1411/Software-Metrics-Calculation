package org.metrics.defectlab.auth.usecase.currentuser;

import org.metrics.defectlab.auth.domain.User;

/** Input boundary: resolves the signed-in account for an existing session. */
public interface GetCurrentUserUseCase {

    User getById(Long userId);
}
