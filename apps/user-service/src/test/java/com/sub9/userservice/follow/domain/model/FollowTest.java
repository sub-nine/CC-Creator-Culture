package com.sub9.userservice.follow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("팔로우 도메인 모델")
class FollowTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("팔로우를 생성하면 CUSTOMER를 생성자와 수정자로 기록한다")
    void when_follow_is_created_customer_audit_information_is_recorded() {
        UUID userId = uuidGenerator.generate();
        Instant createdAt = Instant.parse("2026-09-10T04:00:00Z");

        Follow follow = Follow.create(
                uuidGenerator.generate(), userId, uuidGenerator.generate(), createdAt);

        assertThat(follow.getUserId()).isEqualTo(userId);
        assertThat(follow.getCreatedBy()).isEqualTo(userId);
        assertThat(follow.getUpdatedBy()).isEqualTo(userId);
        assertThat(follow.getCreatedAt()).isEqualTo(createdAt);
        assertThat(follow.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("활성 팔로우를 해제하면 삭제 및 수정 감사를 기록한다")
    void when_active_follow_is_unfollowed_deletion_and_update_audit_are_recorded() {
        UUID userId = uuidGenerator.generate();
        Follow follow = createFollow(userId);
        Instant deletedAt = Instant.parse("2026-09-10T05:00:00Z");

        follow.unfollow(userId, deletedAt);

        assertThat(follow.isDeleted()).isTrue();
        assertThat(follow.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(follow.getDeletedBy()).isEqualTo(userId);
        assertThat(follow.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(follow.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("삭제된 팔로우를 복구하면 최초 생성 이력을 유지한다")
    void when_deleted_follow_is_restored_original_creation_audit_is_preserved() {
        UUID userId = uuidGenerator.generate();
        Follow follow = createFollow(userId);
        UUID followId = follow.getId();
        Instant createdAt = follow.getCreatedAt();
        UUID createdBy = follow.getCreatedBy();
        follow.unfollow(userId, Instant.parse("2026-09-10T05:00:00Z"));
        Instant restoredAt = Instant.parse("2026-09-10T06:00:00Z");

        follow.restore(userId, restoredAt);

        assertThat(follow.isDeleted()).isFalse();
        assertThat(follow.getDeletedAt()).isNull();
        assertThat(follow.getDeletedBy()).isNull();
        assertThat(follow.getId()).isEqualTo(followId);
        assertThat(follow.getCreatedAt()).isEqualTo(createdAt);
        assertThat(follow.getCreatedBy()).isEqualTo(createdBy);
        assertThat(follow.getUpdatedAt()).isEqualTo(restoredAt);
        assertThat(follow.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("팔로우 소유자가 아닌 사용자는 관계를 변경할 수 없다")
    void when_non_owner_changes_follow_relationship_change_is_rejected() {
        UUID userId = uuidGenerator.generate();
        Follow follow = createFollow(userId);

        assertThatThrownBy(() -> follow.unfollow(
                uuidGenerator.generate(), Instant.parse("2026-09-10T05:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UUID v7이 아닌 식별자로 팔로우를 생성할 수 없다")
    void when_follow_id_is_not_uuid_v7_creation_is_rejected() {
        assertThatThrownBy(() -> Follow.create(
                UUID.randomUUID(),
                uuidGenerator.generate(),
                uuidGenerator.generate(),
                Instant.parse("2026-09-10T04:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must be a UUID v7");
    }

    private Follow createFollow(UUID userId) {
        return Follow.create(
                uuidGenerator.generate(),
                userId,
                uuidGenerator.generate(),
                Instant.parse("2026-09-10T04:00:00Z"));
    }
}
