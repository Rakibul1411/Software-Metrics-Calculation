package org.metrics.defectlab.auth.usecase.passwordreset;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestPasswordResetInteractor implements RequestPasswordResetUseCase {

    private final UserRepository userRepository;

    public RequestPasswordResetInteractor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailRegistered(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository.findByEmail(User.normalizeEmail(email)).isPresent();
    }
}
