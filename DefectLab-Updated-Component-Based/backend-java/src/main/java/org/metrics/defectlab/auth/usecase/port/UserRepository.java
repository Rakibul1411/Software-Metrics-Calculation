package org.metrics.defectlab.auth.usecase.port;

import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;

/** Repository interface for persisting and querying user accounts. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
