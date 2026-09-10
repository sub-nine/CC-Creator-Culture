package com.sub9.userservice.follow.domain.repository;

import com.sub9.userservice.follow.domain.model.Follow;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository {

    Follow save(Follow follow);

    void flush();

    Optional<Follow> findByUserIdAndCreatorIdForUpdate(UUID userId, UUID creatorId);
}
