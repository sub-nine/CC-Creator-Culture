package com.sub9.userservice.follow.infrastructure.persistence;

import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepository {

    private final FollowJpaRepository followJpaRepository;

    @Override
    public Follow save(Follow follow) {
        return followJpaRepository.save(follow);
    }

    @Override
    public void flush() {
        followJpaRepository.flush();
    }

    @Override
    public Optional<Follow> findByUserIdAndCreatorIdForUpdate(UUID userId, UUID creatorId) {
        return followJpaRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId);
    }
}
