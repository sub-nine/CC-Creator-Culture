package com.sub9.userservice.user.infrastructure.persistence;

import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    // 도메인 저장소의 요청을 JPA 저장소 호출로 연결 (구현체)

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findActiveById(UUID userId) {
        return userJpaRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Optional<User> findActiveByEmail(String email) {
        return userJpaRepository.findByEmailAndDeletedAtIsNull(email);
    }

    @Override
    public boolean existsByEmailIncludingDeleted(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByNicknameIncludingDeleted(String nickname) {
        return userJpaRepository.existsByNickname(nickname);
    }

    @Override
    public boolean existsByPhoneIncludingDeleted(String phone) {
        return userJpaRepository.existsByPhone(phone);
    }
}
