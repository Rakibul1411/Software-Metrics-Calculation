package org.metrics.defectlab.auth.usecase.port;

/** Output port: how use cases hash and verify passwords, free of any framework detail. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
