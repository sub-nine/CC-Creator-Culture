package com.sub9.userservice.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.domain.exception.AuthenticationTokenStorageException;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import com.sub9.userservice.shared.domain.model.BaseAuditEntity;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import com.sub9.userservice.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("회원 탈퇴 서비스")
class UserDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final long EXPIRES_AT = NOW.plusSeconds(600).getEpochSecond();

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ObjectProvider<LogoutService> logoutServiceProvider;
    @Mock
    private LogoutService logoutService;

    private UserDeletionService userDeletionService;

    @BeforeEach
    void setUp() {
        userDeletionService = new UserDeletionService(
                userRepository, creatorRepository, followRepository, logoutServiceProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(logoutServiceProvider.getIfAvailable()).thenReturn(logoutService);
    }

    @Test
    @DisplayName("CUSTOMER는 토큰 무효화 후 본인의 Follow와 User를 삭제한다")
    void when_customer_deletes_account_token_follow_and_user_are_deleted_in_order() {
        UUID userId = uuidGenerator.generate();
        UUID tokenId = uuidGenerator.generate();
        User user = createUser(userId, UserRole.CUSTOMER);
        when(userRepository.findActiveByIdForUpdate(userId)).thenReturn(Optional.of(user));

        userDeletionService.deleteMyAccount(userId, tokenId, EXPIRES_AT);

        InOrder order = inOrder(userRepository, logoutService, followRepository);
        order.verify(userRepository).findActiveByIdForUpdate(userId);
        order.verify(logoutService).logout(userId, tokenId, EXPIRES_AT);
        order.verify(followRepository).softDeleteActiveByUserId(userId, userId, NOW);
        order.verify(userRepository).flush();
        assertDeletionAudit(user, userId);
        verifyNoInteractions(creatorRepository);
    }

    @Test
    @DisplayName("CREATOR는 대상 Follow와 Creator와 User를 삭제한다")
    void when_creator_deletes_account_follow_creator_and_user_are_deleted() {
        UUID userId = uuidGenerator.generate();
        UUID tokenId = uuidGenerator.generate();
        User user = createUser(userId, UserRole.CREATOR);
        Creator creator = Creator.createPending(
                uuidGenerator.generate(), userId, "창작상점", "1234567890", NOW.minusSeconds(60));
        when(userRepository.findActiveByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(creatorRepository.findActiveByUserIdForUpdate(userId))
                .thenReturn(Optional.of(creator));

        userDeletionService.deleteMyAccount(userId, tokenId, EXPIRES_AT);

        verify(logoutService).logout(userId, tokenId, EXPIRES_AT);
        verify(followRepository).softDeleteActiveByCreatorId(creator.getId(), userId, NOW);
        verify(followRepository, never()).softDeleteActiveByUserId(userId, userId, NOW);
        assertDeletionAudit(user, userId);
        assertDeletionAudit(creator, userId);
    }

    @Test
    @DisplayName("Redis 토큰 무효화에 실패하면 DB 삭제를 수행하지 않는다")
    void when_token_invalidation_fails_database_is_not_changed() {
        UUID userId = uuidGenerator.generate();
        UUID tokenId = uuidGenerator.generate();
        User user = createUser(userId, UserRole.CUSTOMER);
        when(userRepository.findActiveByIdForUpdate(userId)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new AuthenticationTokenStorageException())
                .when(logoutService).logout(userId, tokenId, EXPIRES_AT);

        assertThatThrownBy(() ->
                userDeletionService.deleteMyAccount(userId, tokenId, EXPIRES_AT))
                .isInstanceOf(AuthenticationTokenStorageException.class);

        assertThat(user.isDeleted()).isFalse();
        verifyNoInteractions(followRepository, creatorRepository);
        verify(userRepository, never()).flush();
    }

    @Test
    @DisplayName("활성 사용자가 없으면 사용자 없음 오류를 반환한다")
    void when_active_user_does_not_exist_delete_my_account_returns_not_found() {
        UUID userId = uuidGenerator.generate();
        when(userRepository.findActiveByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDeletionService.deleteMyAccount(
                userId, uuidGenerator.generate(), EXPIRES_AT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verifyNoInteractions(logoutService, followRepository, creatorRepository);
    }

    @Test
    @DisplayName("MASTER와 MANAGER는 본인 계정을 삭제할 수 없다")
    void when_admin_deletes_account_access_is_denied() {
        for (UserRole role : new UserRole[]{UserRole.MASTER, UserRole.MANAGER}) {
            UUID userId = uuidGenerator.generate();
            when(userRepository.findActiveByIdForUpdate(userId))
                    .thenReturn(Optional.of(createUser(userId, role)));

            assertThatThrownBy(() -> userDeletionService.deleteMyAccount(
                    userId, uuidGenerator.generate(), EXPIRES_AT))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(logoutService, followRepository, creatorRepository);
    }

    private User createUser(UUID userId, UserRole role) {
        return User.create(
                userId, role.name().toLowerCase() + "@example.com", "encoded-password",
                role.name().toLowerCase(), "01012345678", "서울시 예시구", null, role,
                NOW.minusSeconds(60));
    }

    private void assertDeletionAudit(BaseAuditEntity entity, UUID actorId) {
        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedAt()).isEqualTo(NOW);
        assertThat(entity.getDeletedBy()).isEqualTo(actorId);
        assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
        assertThat(entity.getUpdatedBy()).isEqualTo(actorId);
    }
}
