package com.sub9.userservice.user.infrastructure.persistence;

import com.sub9.userservice.user.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, UUID> {
    // JPA를 통한 실제 DB 접근

    Optional<User> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhone(String phone);
}
