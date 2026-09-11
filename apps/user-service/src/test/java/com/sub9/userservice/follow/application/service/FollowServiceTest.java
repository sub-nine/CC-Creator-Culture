package com.sub9.userservice.follow.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.follow.domain.exception.FollowErrorCode;
import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.follow.domain.repository.FollowRepository;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("팔로우·언팔로우 서비스")
class FollowServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T07:00:00Z");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Mock
    private FollowRepository followRepository;
    @Mock
    private CreatorRepository creatorRepository;

    private FollowService followService;

    @BeforeEach
    void setUp() {
        followService = new FollowService(
                followRepository,
                creatorRepository,
                uuidGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("CUSTOMER가 승인된 Creator를 처음 팔로우하면 새 관계를 저장한다")
    void when_customer_follows_approved_creator_new_follow_is_saved() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        givenApprovedCreator(creatorId);
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.empty());

        var response = followService.follow(userId, creatorId);

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        verify(followRepository).flush();
        Follow follow = captor.getValue();
        assertThat(follow.getUserId()).isEqualTo(userId);
        assertThat(follow.getCreatorId()).isEqualTo(creatorId);
        assertThat(follow.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.creatorId()).isEqualTo(creatorId);
        assertThat(response.following()).isTrue();
    }

    @Test
    @DisplayName("삭제된 관계의 Creator를 다시 팔로우하면 기존 관계를 복구한다")
    void when_customer_refollows_creator_deleted_follow_is_restored() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        Follow follow = Follow.create(uuidGenerator.generate(), userId, creatorId, NOW.minusSeconds(60));
        follow.unfollow(userId, NOW.minusSeconds(30));
        UUID followId = follow.getId();
        Instant createdAt = follow.getCreatedAt();
        givenApprovedCreator(creatorId);
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.of(follow));

        followService.follow(userId, creatorId);

        assertThat(follow.isDeleted()).isFalse();
        assertThat(follow.getId()).isEqualTo(followId);
        assertThat(follow.getCreatedAt()).isEqualTo(createdAt);
        assertThat(follow.getUpdatedAt()).isEqualTo(NOW);
        verify(followRepository).save(follow);
        verify(followRepository).flush();
    }

    @Test
    @DisplayName("활성 관계가 있으면 중복 팔로우 오류를 반환한다")
    void when_active_follow_exists_follow_returns_conflict() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        Follow follow = Follow.create(uuidGenerator.generate(), userId, creatorId, NOW);
        givenApprovedCreator(creatorId);
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.of(follow));

        assertFollowError(
                () -> followService.follow(userId, creatorId),
                FollowErrorCode.FOLLOW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("팔로우 가능한 Creator가 없으면 대상 없음 오류를 반환한다")
    void when_approved_active_creator_does_not_exist_follow_returns_not_found() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        when(creatorRepository.findApprovedActiveById(creatorId)).thenReturn(Optional.empty());

        assertFollowError(
                () -> followService.follow(userId, creatorId),
                FollowErrorCode.FOLLOW_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("신규 팔로우 저장 중 UNIQUE 충돌이 발생하면 중복 오류를 반환한다")
    void when_concurrent_follow_insert_conflicts_follow_returns_conflict() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        givenApprovedCreator(creatorId);
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(followRepository).flush();

        assertFollowError(
                () -> followService.follow(userId, creatorId),
                FollowErrorCode.FOLLOW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("CUSTOMER가 활성 관계를 언팔로우하면 Soft Delete한다")
    void when_customer_unfollows_active_creator_follow_is_soft_deleted() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        Follow follow = Follow.create(uuidGenerator.generate(), userId, creatorId, NOW.minusSeconds(60));
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.of(follow));

        followService.unfollow(userId, creatorId);

        assertThat(follow.isDeleted()).isTrue();
        assertThat(follow.getDeletedAt()).isEqualTo(NOW);
        assertThat(follow.getDeletedBy()).isEqualTo(userId);
        verify(followRepository).flush();
    }

    @Test
    @DisplayName("활성 팔로우 관계가 없으면 언팔로우 대상 없음 오류를 반환한다")
    void when_active_follow_does_not_exist_unfollow_returns_not_found() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        when(followRepository.findByUserIdAndCreatorIdForUpdate(userId, creatorId))
                .thenReturn(Optional.empty());

        assertFollowError(
                () -> followService.unfollow(userId, creatorId),
                FollowErrorCode.FOLLOW_NOT_FOUND);
    }

    @Test
    @DisplayName("활성 관계가 있으면 팔로우 여부 조회에서 true를 반환한다")
    void when_active_follow_exists_follow_status_returns_true() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        givenApprovedCreator(creatorId);
        when(followRepository.existsActiveByUserIdAndCreatorId(userId, creatorId))
                .thenReturn(true);

        var response = followService.getFollowStatus(userId, creatorId);

        assertThat(response.creatorId()).isEqualTo(creatorId);
        assertThat(response.following()).isTrue();
    }

    @Test
    @DisplayName("활성 관계가 없으면 팔로우 여부 조회에서 false를 반환한다")
    void when_active_follow_does_not_exist_follow_status_returns_false() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        givenApprovedCreator(creatorId);
        when(followRepository.existsActiveByUserIdAndCreatorId(userId, creatorId))
                .thenReturn(false);

        var response = followService.getFollowStatus(userId, creatorId);

        assertThat(response.creatorId()).isEqualTo(creatorId);
        assertThat(response.following()).isFalse();
    }

    @Test
    @DisplayName("팔로우 가능한 Creator가 없으면 팔로우 여부 조회에서 대상 없음 오류를 반환한다")
    void when_approved_active_creator_does_not_exist_follow_status_returns_not_found() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        when(creatorRepository.findApprovedActiveById(creatorId)).thenReturn(Optional.empty());

        assertFollowError(
                () -> followService.getFollowStatus(userId, creatorId),
                FollowErrorCode.FOLLOW_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("팔로우 목록을 조회하면 고정 정렬과 페이지 정보로 응답한다")
    void when_customer_reads_followed_creators_sorted_page_response_is_returned() {
        UUID userId = uuidGenerator.generate();
        UUID creatorId = uuidGenerator.generate();
        Follow follow = mock(Follow.class);
        when(follow.getCreatorId()).thenReturn(creatorId);
        when(follow.getCreatorName()).thenReturn("트렌드샵");
        when(follow.getCreatedAt()).thenReturn(NOW);
        when(followRepository.findActiveByUserId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(follow)));

        var response = followService.getFollowedCreators(userId, 0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(followRepository).findActiveByUserId(org.mockito.ArgumentMatchers.eq(userId), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.creatorId()).isEqualTo(creatorId);
            assertThat(item.creatorName()).isEqualTo("트렌드샵");
            assertThat(item.followedAt()).isEqualTo(NOW);
        });
        assertThat(response.totalElements()).isEqualTo(1);
    }

    private void givenApprovedCreator(UUID creatorId) {
        when(creatorRepository.findApprovedActiveById(creatorId))
                .thenReturn(Optional.of(mock(Creator.class)));
    }

    private void assertFollowError(Runnable action, FollowErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
