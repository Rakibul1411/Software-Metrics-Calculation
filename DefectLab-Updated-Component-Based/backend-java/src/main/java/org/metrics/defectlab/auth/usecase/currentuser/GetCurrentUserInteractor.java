package org.metrics.defectlab.auth.usecase.currentuser;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.exception.InvalidCredentialsException;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentUserInteractor implements GetCurrentUserUseCase {

    private final UserRepository userRepository;

    public GetCurrentUserInteractor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("The session is no longer valid."));
    }
}
