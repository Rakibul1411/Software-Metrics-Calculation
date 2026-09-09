package org.metrics.defectlab.auth.usecase.changepassword;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.PasswordPolicy;
import org.metrics.defectlab.auth.usecase.currentuser.GetCurrentUserUseCase;
import org.metrics.defectlab.auth.usecase.exception.InvalidCredentialsException;
import org.metrics.defectlab.auth.usecase.port.PasswordHasher;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangePasswordInteractor implements ChangePasswordUseCase {

    private final GetCurrentUserUseCase getCurrentUser;
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public ChangePasswordInteractor(GetCurrentUserUseCase getCurrentUser, UserRepository userRepository,
            PasswordHasher passwordHasher) {
        this.getCurrentUser = getCurrentUser;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getCurrentUser.getById(userId);
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("The current password is incorrect.");
        }
        PasswordPolicy.validate(newPassword);
        user.changePasswordHash(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }
}
