package org.metrics.defectlab.auth.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * Enterprise Business Rule: a registered account and the invariants that hold
 * for it regardless of how it is persisted or delivered. Carries no
 * persistence or framework annotations.
 */
public class User {

    private final Long id;
    private final String name;
    private final String email;
    private String passwordHash;
    private final Instant createdAt;

    public User(Long id, String name, String email, String passwordHash, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /** A brand-new account; the id is assigned once the repository persists it. */
    public static User newRegistration(String name, String email, String passwordHash) {
        return new User(null, name, email, passwordHash, Instant.now());
    }

    /** The schema stores addresses lowercase so uniqueness is case-insensitive. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
