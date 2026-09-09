package org.metrics.defectlab.auth.usecase.login;

import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.exception.InvalidCredentialsException;
import org.metrics.defectlab.auth.usecase.port.PasswordHasher;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserInteractor implements AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticateUserInteractor(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    /**
     * The same message is returned for an unknown address and a wrong password
     * so the response cannot enumerate accounts.
     */
    @Override
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty() || !passwordHasher.matches(password, found.get().getPasswordHash())) {
            throw new InvalidCredentialsException("The email or password is incorrect.");
        }
        return found.get();
    }
}
