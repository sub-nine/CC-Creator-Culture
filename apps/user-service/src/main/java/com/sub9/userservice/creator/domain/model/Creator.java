package com.sub9.userservice.creator.domain.model;

import com.sub9.userservice.shared.domain.model.BaseAuditEntity;
import com.sub9.userservice.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "p_creators",
        schema = "private",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_creators_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_creators_business_number",
                        columnNames = "business_registration_number"),
                @UniqueConstraint(name = "uk_creators_name", columnNames = "creator_name")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Creator extends BaseAuditEntity {

    @Id
    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;     // 창작자 정보의 식별자

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;        // 연결된 로그인 계정의 식별자

    @Column(name = "creator_name", nullable = false, length = 100)
    private String creatorName;     // 상호명

    @Column(name = "business_registration_number", nullable = false, length = 20)
    private String businessRegistrationNumber;      // 사업자등록번호

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_at", columnDefinition = "timestamp")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    // UUID 필드는 사용자 조회 없이 값을 기록하고, 읽기 전용 연관관계는 물리적 FK를 생성한다.
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_creators_user"))
    private User user;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_creators_approved_by"))
    private User approvedByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_creators_created_by"))
    private User createdByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_creators_updated_by"))
    private User updatedByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_creators_deleted_by"))
    private User deletedByUser;

    private Creator(UUID creatorId, UUID userId, String creatorName,
            String businessRegistrationNumber, Instant now) {
        // 필수값 누락을 생성 시점에 차단해 불완전한 Creator가 만들어지지 않도록 한다.
        this.creatorId = requireUuidV7(creatorId, "creatorId");
        this.userId = requireUuidV7(userId, "userId");
        this.creatorName = Objects.requireNonNull(creatorName, "creatorName must not be null");
        this.businessRegistrationNumber = Objects.requireNonNull(
                businessRegistrationNumber, "businessRegistrationNumber must not be null");
        this.approvalStatus = ApprovalStatus.PENDING;
        // 승인 관리자를 기록할 필드이므로 가입 시에는 비워 두고, 가입자를 수정자로 기록한다.
        this.approvedAt = null;
        this.approvedBy = null;
        initializeAudit(null, userId, now);
    }

    public static Creator createPending(UUID creatorId, UUID userId, String creatorName,
            String businessRegistrationNumber, Instant now) {
        return new Creator(creatorId, userId, creatorName, businessRegistrationNumber, now);
    }

    public void softDelete(UUID actorId, Instant now) {
        // 행을 남겨 계정과의 연결 및 변경 이력을 유지한다.
        markDeleted(actorId, now);
    }

    private static UUID requireUuidV7(UUID id, String fieldName) {
        Objects.requireNonNull(id, fieldName + " must not be null");
        if (id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException(fieldName + " must be a UUID v7");
        }
        return id;
    }
}
