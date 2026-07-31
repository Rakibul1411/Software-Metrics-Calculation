package org.metrics.defectlab.auth.application;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.persistence.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int MAXIMUM_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(String name, String email, String password) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("Name may not exceed 100 characters.");
        }
        String normalizedEmail = User.normalizeEmail(email);
        if (normalizedEmail == null || !normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (normalizedEmail.length() > 150) {
            throw new IllegalArgumentException("Email may not exceed 150 characters.");
        }
        validateNewPassword(password);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account already uses that email address.");
        }
        return userRepository.save(
                new User(trimmedName, normalizedEmail, passwordEncoder.encode(password)));
    }

    /**
     * Verifies the password hash. The same message is returned for an unknown
     * address and a wrong password so the response cannot enumerate accounts.
     */
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty() || password == null
                || !passwordEncoder.matches(password, found.get().getPasswordHash())) {
            throw new InvalidCredentialsException("The email or password is incorrect.");
        }
        return found.get();
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("The session is no longer valid."));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("The current password is incorrect.");
        }
        validateNewPassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private static void validateNewPassword(String password) {
        if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "The password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "The password must not exceed 72 UTF-8 bytes.");
        }
    }

    /** Raised for authentication failures so the API can answer 401 rather than 400. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }
}
