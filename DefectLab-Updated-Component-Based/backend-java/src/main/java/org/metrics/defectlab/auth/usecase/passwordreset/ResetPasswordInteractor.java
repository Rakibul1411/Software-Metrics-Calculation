package org.metrics.defectlab.auth.usecase.passwordreset;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.PasswordPolicy;
import org.metrics.defectlab.auth.usecase.port.PasswordHasher;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResetPasswordInteractor implements ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public ResetPasswordInteractor(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account uses that email address."));
        PasswordPolicy.validate(newPassword);
        user.changePasswordHash(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }
}
