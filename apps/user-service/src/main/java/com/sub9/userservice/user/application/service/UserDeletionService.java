package com.sub9.userservice.user.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserRepository userRepository;
    private final CreatorRepository creatorRepository;
    private final FollowRepository followRepository;
    private final ObjectProvider<LogoutService> logoutServiceProvider;
    private final Clock clock;

    @Transactional
    public void deleteMyAccount(
            UUID userId, UUID accessTokenId, long expiresAtEpochSecond) {
        // 활성 사용자를 쓰기 잠금으로 조회해 중복 탈퇴와 동시 변경을 방지
        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        validateDeletableRole(user.getRole());

        // DB 데이터를 변경하기 전에 Refresh Token을 제거하고 현재 Access Token을 무효화
        invalidateTokens(userId, accessTokenId, expiresAtEpochSecond);

        Instant deletedAt = clock.instant();
        if (user.getRole() == UserRole.CREATOR) {
            // CREATOR는 대상 Follow와 연결된 Creator를 같은 트랜잭션에서 함께 Soft Delete
            deleteCreator(userId, deletedAt);
        } else {
            // CUSTOMER는 본인이 생성한 모든 활성 Follow를 Soft Delete
            followRepository.softDeleteActiveByUserId(userId, userId, deletedAt);
        }
        // 연관 데이터 정리가 끝난 후 사용자 계정을 Soft Delete
        user.softDelete(userId, deletedAt);
        userRepository.flush();
    }

    private void deleteCreator(UUID userId, Instant deletedAt) {
        Creator creator = creatorRepository.findActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));
        followRepository.softDeleteActiveByCreatorId(creator.getId(), userId, deletedAt);
        creator.softDelete(userId, deletedAt);
    }

    private void validateDeletableRole(UserRole role) {
        if (role != UserRole.CUSTOMER && role != UserRole.CREATOR) {
            throw new AccessDeniedException("User role cannot delete its own account");
        }
    }

    private void invalidateTokens(UUID userId, UUID accessTokenId, long expiresAtEpochSecond) {
        LogoutService logoutService = logoutServiceProvider.getIfAvailable();
        if (logoutService == null) {
            throw new AuthenticationTokenStorageException();
        }
        logoutService.logout(userId, accessTokenId, expiresAtEpochSecond);
    }
}
