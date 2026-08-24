package org.metrics.defectlab.auth.usecase.port;

import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;

/** Output port: how use cases persist and look up accounts, free of any framework detail. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
