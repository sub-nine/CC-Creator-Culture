package com.sub9.userservice.follow.domain.repository;

import com.sub9.userservice.follow.domain.model.Follow;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FollowRepository {

    Follow save(Follow follow);

    void flush();

    Optional<Follow> findByUserIdAndCreatorIdForUpdate(UUID userId, UUID creatorId);

    boolean existsActiveByUserIdAndCreatorId(UUID userId, UUID creatorId);

    Page<Follow> findActiveByUserId(UUID userId, Pageable pageable);
}
