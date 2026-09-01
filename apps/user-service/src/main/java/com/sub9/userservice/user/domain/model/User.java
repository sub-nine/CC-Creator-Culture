package com.sub9.userservice.user.domain.model;

import com.sub9.userservice.shared.domain.model.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "p_users",
        schema = "private",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname"),
                @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
        },
        indexes = @Index(name = "idx_users_role", columnList = "role"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseAuditEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "slack_id", length = 100)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    // UUID 필드는 사용자 조회 없이 감사 값을 기록하고, 읽기 전용 연관관계는 물리적 FK를 생성한다.
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_users_created_by"))
    private User createdByUser;

    // JPA가 물리적 FK를 생성하도록 관계를 표현하는 필드
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_users_updated_by"))
    private User updatedByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_users_deleted_by"))
    private User deletedByUser;

    private User(UUID userId, String email, String encodedPassword, String nickname,
            String phone, String address, String slackId, UserRole role, Instant now) {
        // 필수값 누락을 객체 생성 시점에 차단해 불완전한 User가 만들어지지 않도록 한다.
        this.userId = requireUuidV7(userId, "userId");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.password = Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
        this.phone = Objects.requireNonNull(phone, "phone must not be null");
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.slackId = slackId;
        this.role = Objects.requireNonNull(role, "role must not be null");
        // 가입 시에는 인증 정보가 없으므로 미리 생성한 사용자 ID를 수정자로 기록한다.
        initializeAudit(null, userId, now);
    }

    // 비밀번호 해시와 역할은 가입 유스케이스에서 준비하며, 요청 DTO를 그대로 전달하지 않는다.
    public static User create(UUID userId, String email, String encodedPassword, String nickname,
            String phone, String address, String slackId, UserRole role, Instant now) {
        return new User(userId, email, encodedPassword, nickname, phone, address, slackId, role, now);
    }

    public void softDelete(UUID actorId, Instant now) {
        // 행을 남겨 참조와 이력을 유지하고, 삭제와 수정 감사 정보를 함께 갱신한다.
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
