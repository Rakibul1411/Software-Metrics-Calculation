package org.metrics.defectlab.auth.persistence;

import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
