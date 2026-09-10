package com.sub9.userservice.creator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(creator.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("PENDING 창작자를 승인하면 승인 및 감사 정보를 관리자 기준으로 기록한다")
    void when_pending_creator_is_approved_approval_and_audit_are_recorded() {
        UUID adminId = uuidGenerator.generate();
        Instant approvedAt = Instant.parse("2026-09-10T02:00:00Z");
        Creator creator = pendingCreator();

        creator.approve(adminId, approvedAt);

        assertThat(creator.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(creator.getApprovedBy()).isEqualTo(adminId);
        assertThat(creator.getApprovedAt())
                .isEqualTo(LocalDateTime.parse("2026-09-10T02:00:00"));
        assertThat(creator.getCreatedBy()).isEqualTo(adminId);
        assertThat(creator.getUpdatedBy()).isEqualTo(adminId);
        assertThat(creator.getUpdatedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("PENDING 창작자를 거절하면 승인 정보 없이 수정 감사를 기록한다")
    void when_pending_creator_is_rejected_only_update_audit_is_recorded() {
        UUID adminId = uuidGenerator.generate();
        Instant rejectedAt = Instant.parse("2026-09-10T03:00:00Z");
        Creator creator = pendingCreator();

        creator.reject(adminId, rejectedAt);

        assertThat(creator.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(creator.getApprovedBy()).isNull();
        assertThat(creator.getApprovedAt()).isNull();
        assertThat(creator.getCreatedBy()).isNull();
        assertThat(creator.getUpdatedBy()).isEqualTo(adminId);
        assertThat(creator.getUpdatedAt()).isEqualTo(rejectedAt);
    }

    @Test
    @DisplayName("이미 심사된 창작자는 다시 심사할 수 없다")
    void when_creator_is_already_reviewed_another_review_is_rejected() {
        Creator creator = pendingCreator();
        creator.approve(uuidGenerator.generate(), Instant.parse("2026-09-10T02:00:00Z"));

        assertThatThrownBy(() -> creator.reject(
                uuidGenerator.generate(), Instant.parse("2026-09-10T03:00:00Z")))
                .isInstanceOf(IllegalStateException.class);
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

        assertThat(creator.getDeletedAt()).isEqualTo(Instant.parse("2026-09-01T03:00:00Z"));
        assertThat(creator.getDeletedBy()).isEqualTo(firstActorId);
        assertThat(creator.getUpdatedAt()).isEqualTo(creator.getDeletedAt());
        assertThat(creator.getUpdatedBy()).isEqualTo(firstActorId);
    }

    private Creator pendingCreator() {
        return Creator.createPending(
                uuidGenerator.generate(), uuidGenerator.generate(), "창작상점", "123-45-67890",
                Instant.parse("2026-09-01T02:00:00Z"));
    }
}
