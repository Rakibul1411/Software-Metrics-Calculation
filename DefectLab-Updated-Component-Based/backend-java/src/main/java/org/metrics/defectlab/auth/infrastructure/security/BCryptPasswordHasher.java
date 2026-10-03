package org.metrics.defectlab.auth.infrastructure.security;

import org.metrics.defectlab.auth.usecase.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password hashing implementation using Spring Security's BCryptPasswordEncoder.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && encoder.matches(rawPassword, hash);
    }
}
