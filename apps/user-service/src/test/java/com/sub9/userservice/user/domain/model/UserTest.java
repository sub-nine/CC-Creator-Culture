package com.sub9.userservice.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사용자 도메인 모델")
class UserTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("가입 사용자는 생성 시각과 수정자를 UTC 기준으로 기록한다")
    void when_user_is_created_audit_fields_are_initialized_in_utc() {
        UUID userId = uuidGenerator.generate();
        Instant createdAt = Instant.parse("2026-09-01T01:30:00Z");

        User user = createUser(userId, "user@example.com", "nickname", "010-1111-2222", createdAt);

        assertThat(user.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 1, 30));
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(user.getCreatedBy()).isNull();
        assertThat(user.getUpdatedBy()).isEqualTo(userId);
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("사용자 삭제는 행을 유지하기 위한 삭제 및 수정 감사를 함께 기록한다")
    void when_user_is_soft_deleted_deletion_and_update_audit_are_recorded() {
        UUID userId = uuidGenerator.generate();
        UUID actorId = uuidGenerator.generate();
        Instant deletedAt = Instant.parse("2026-09-01T03:00:00Z");
        User user = createUser(userId, "user@example.com", "nickname", "010-1111-2222",
                Instant.parse("2026-09-01T01:30:00Z"));

        user.softDelete(actorId, deletedAt);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 3, 0));
        assertThat(user.getDeletedBy()).isEqualTo(actorId);
        assertThat(user.getUpdatedAt()).isEqualTo(user.getDeletedAt());
        assertThat(user.getUpdatedBy()).isEqualTo(actorId);
    }

    @Test
    @DisplayName("UUID v7이 아닌 식별자로 사용자를 생성할 수 없다")
    void when_user_id_is_not_uuid_v7_creation_is_rejected() {
        UUID uuidV4 = UUID.randomUUID();

        assertThatThrownBy(() -> createUser(uuidV4, "user@example.com", "nickname",
                "010-1111-2222", Instant.parse("2026-09-01T01:30:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be a UUID v7");
    }

    private User createUser(UUID userId, String email, String nickname, String phone, Instant now) {
        return User.create(userId, email, "encoded-password", nickname, phone,
                "서울시 예시구", null, UserRole.CUSTOMER, now);
    }
}
