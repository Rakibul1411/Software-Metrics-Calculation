package org.metrics.defectlab.auth.usecase.port;

/** Service interface for hashing and verifying user passwords. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
