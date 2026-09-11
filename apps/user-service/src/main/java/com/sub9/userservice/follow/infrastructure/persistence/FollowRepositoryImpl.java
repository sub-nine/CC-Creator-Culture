package com.sub9.userservice.follow.infrastructure.persistence;

import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Override
    public boolean existsActiveByUserIdAndCreatorId(UUID userId, UUID creatorId) {
        return followJpaRepository.existsByUserIdAndCreatorIdAndDeletedAtIsNull(userId, creatorId);
    }

    @Override
    public Page<Follow> findActiveByUserId(UUID userId, Pageable pageable) {
        return followJpaRepository
                .findAllByUserIdAndDeletedAtIsNullAndCreator_ApprovalStatusAndCreator_DeletedAtIsNull(
                        userId, ApprovalStatus.APPROVED, pageable);
    }
}
