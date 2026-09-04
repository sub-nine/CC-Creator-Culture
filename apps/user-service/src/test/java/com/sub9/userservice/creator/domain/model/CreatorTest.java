package com.sub9.userservice.creator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.identifier.UuidV7Generator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("창작자 도메인 모델")
class CreatorTest {

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Test
    @DisplayName("창작자 가입 신청은 PENDING 상태와 승인 전 감사 정보로 생성된다")
    void when_creator_is_created_approval_and_audit_fields_are_initialized() {
        UUID creatorId = uuidGenerator.generate();
        UUID userId = uuidGenerator.generate();
        Instant createdAt = Instant.parse("2026-09-01T02:00:00Z");

        Creator creator = Creator.createPending(creatorId, userId, "창작상점", "123-45-67890",
                createdAt);

        assertThat(creator.getId()).isEqualTo(creatorId);
        assertThat(creator.getUserId()).isEqualTo(userId);
        assertThat(creator.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(creator.getApprovedAt()).isNull();
        assertThat(creator.getApprovedBy()).isNull();
        assertThat(creator.getCreatedBy()).isNull();
        assertThat(creator.getUpdatedBy()).isEqualTo(userId);
        assertThat(creator.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 2, 0));
    }

    @Test
    @DisplayName("창작자 삭제를 반복해도 최초 삭제 이력을 유지한다")
    void when_creator_is_deleted_repeatedly_first_deletion_audit_is_preserved() {
        UUID userId = uuidGenerator.generate();
        UUID firstActorId = uuidGenerator.generate();
        Creator creator = Creator.createPending(uuidGenerator.generate(), userId, "창작상점",
                "123-45-67890", Instant.parse("2026-09-01T02:00:00Z"));

        creator.softDelete(firstActorId, Instant.parse("2026-09-01T03:00:00Z"));
        creator.softDelete(uuidGenerator.generate(), Instant.parse("2026-09-01T04:00:00Z"));

        assertThat(creator.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 3, 0));
        assertThat(creator.getDeletedBy()).isEqualTo(firstActorId);
        assertThat(creator.getUpdatedAt()).isEqualTo(creator.getDeletedAt());
        assertThat(creator.getUpdatedBy()).isEqualTo(firstActorId);
    }
}
