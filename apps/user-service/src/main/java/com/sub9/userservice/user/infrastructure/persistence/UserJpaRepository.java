package com.sub9.userservice.user.infrastructure.persistence;

import com.sub9.userservice.user.domain.model.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<User, UUID> {
    // JPA를 통한 실제 DB 접근

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user "
            + "where user.id = :userId and user.deletedAt is null")
    Optional<User> findActiveByIdForUpdate(@Param("userId") UUID userId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhone(String phone);
}
