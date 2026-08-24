package org.metrics.defectlab.auth.usecase.register;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.PasswordPolicy;
import org.metrics.defectlab.auth.usecase.port.PasswordHasher;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserInteractor implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUserInteractor(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        String trimmedName = command.name() == null ? "" : command.name().trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("A name is required.");
        }
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("Name may not exceed 100 characters.");
        }
        String normalizedEmail = User.normalizeEmail(command.email());
        if (normalizedEmail == null || !normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (normalizedEmail.length() > 150) {
            throw new IllegalArgumentException("Email may not exceed 150 characters.");
        }
        PasswordPolicy.validate(command.password());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account already uses that email address.");
        }
        User user = User.newRegistration(trimmedName, normalizedEmail, passwordHasher.hash(command.password()));
        return userRepository.save(user);
    }
}
