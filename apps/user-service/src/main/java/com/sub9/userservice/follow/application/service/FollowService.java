package com.sub9.userservice.follow.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.exception.FollowErrorCode;
import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.follow.presentation.response.FollowStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final CreatorRepository creatorRepository;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    @Transactional
    public FollowStatusResponse follow(UUID userId, UUID creatorId) {
        creatorRepository.findApprovedActiveById(creatorId)
                .orElseThrow(() -> new BusinessException(FollowErrorCode.FOLLOW_TARGET_NOT_FOUND));

        Instant now = clock.instant();
        Follow follow = followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId)
                .map(existingFollow -> restore(existingFollow, userId, now))
                .orElseGet(() -> Follow.create(
                        uuidV7Generator.generate(), userId, creatorId, now));

        try {
            followRepository.save(follow);
            followRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(FollowErrorCode.FOLLOW_ALREADY_EXISTS);
        }
        return FollowStatusResponse.followed(creatorId);
    }

    @Transactional
    public void unfollow(UUID userId, UUID creatorId) {
        Follow follow = followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId)
                .filter(existingFollow -> !existingFollow.isDeleted())
                .orElseThrow(() -> new BusinessException(FollowErrorCode.FOLLOW_NOT_FOUND));

        follow.unfollow(userId, clock.instant());
        followRepository.flush();
    }

    private Follow restore(Follow follow, UUID userId, Instant now) {
        if (!follow.isDeleted()) {
            throw new BusinessException(FollowErrorCode.FOLLOW_ALREADY_EXISTS);
        }
        follow.restore(userId, now);
        return follow;
    }
}
