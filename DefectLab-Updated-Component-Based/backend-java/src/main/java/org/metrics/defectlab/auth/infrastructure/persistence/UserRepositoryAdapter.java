package org.metrics.defectlab.auth.infrastructure.persistence;

import java.util.Optional;

import org.metrics.defectlab.auth.domain.User;
import org.metrics.defectlab.auth.usecase.port.UserRepository;
import org.springframework.stereotype.Repository;

/** Gateway: fulfils the {@link UserRepository} port on top of Spring Data JPA. */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpaRepository;

    public UserRepositoryAdapter(SpringDataUserRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        return toDomain(jpaRepository.save(toJpaEntity(user)));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private static UserJpaEntity toJpaEntity(User user) {
        return new UserJpaEntity(user.getId(), user.getName(), user.getEmail(),
                user.getPasswordHash(), user.getCreatedAt());
    }

    private static User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getName(), entity.getEmail(),
                entity.getPasswordHash(), entity.getCreatedAt());
    }
}
