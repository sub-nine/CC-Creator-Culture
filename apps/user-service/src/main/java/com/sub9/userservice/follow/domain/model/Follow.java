package com.sub9.userservice.follow.domain.model;

import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.shared.domain.model.BaseAuditEntity;
import com.sub9.userservice.user.domain.model.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "p_follows",
        schema = "private",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_user_creator",
                columnNames = {"user_id", "creator_id"}),
        indexes = {
                @Index(name = "idx_follows_user", columnList = "user_id"),
                @Index(name = "idx_follows_creator", columnList = "creator_id")
        })
@AttributeOverride(
        name = "createdBy",
        column = @Column(name = "created_by", nullable = false, updatable = false))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends BaseAuditEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_follows_user"))
    private User user;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_follows_creator"))
    private Creator creator;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_follows_created_by"))
    private User createdByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false, insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_follows_updated_by"))
    private User updatedByUser;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_follows_deleted_by"))
    private User deletedByUser;

    private Follow(UUID id, UUID userId, UUID creatorId, Instant now) {
        this.id = requireUuidV7(id, "id");
        this.userId = requireUuidV7(userId, "userId");
        this.creatorId = requireUuidV7(creatorId, "creatorId");
        initializeAudit(this.userId, this.userId, now);
    }

    public static Follow create(UUID id, UUID userId, UUID creatorId, Instant now) {
        return new Follow(id, userId, creatorId, now);
    }

    public String getCreatorName() {
        return creator.getCreatorName();
    }

    public void unfollow(UUID actorId, Instant now) {
        if (isDeleted()) {
            throw new IllegalStateException("Only active follows can be unfollowed");
        }
        markDeleted(requireOwner(actorId), now);
    }

    public void restore(UUID actorId, Instant now) {
        if (!isDeleted()) {
            throw new IllegalStateException("Only deleted follows can be restored");
        }
        restoreDeleted(requireOwner(actorId), now);
    }

    private UUID requireOwner(UUID actorId) {
        UUID ownerId = requireUuidV7(actorId, "actorId");
        if (!userId.equals(ownerId)) {
            throw new IllegalArgumentException("Only the follow owner can change the relationship");
        }
        return ownerId;
    }

    private static UUID requireUuidV7(UUID id, String fieldName) {
        Objects.requireNonNull(id, fieldName + " must not be null");
        if (id.version() != 7 || id.variant() != 2) {
            throw new IllegalArgumentException(fieldName + " must be a UUID v7");
        }
        return id;
    }
}
