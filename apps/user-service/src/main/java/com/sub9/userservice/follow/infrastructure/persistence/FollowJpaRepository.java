package com.sub9.userservice.follow.infrastructure.persistence;

import com.sub9.userservice.follow.domain.model.Follow;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowJpaRepository extends JpaRepository<Follow, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select follow from Follow follow "
            + "where follow.userId = :userId and follow.creatorId = :creatorId")
    Optional<Follow> findByUserIdAndCreatorIdForUpdate(
            @Param("userId") UUID userId,
            @Param("creatorId") UUID creatorId);

    boolean existsByUserIdAndCreatorIdAndDeletedAtIsNull(UUID userId, UUID creatorId);

    @EntityGraph(attributePaths = "creator")
    Page<Follow> findAllByUserIdAndDeletedAtIsNullAndCreator_ApprovalStatusAndCreator_DeletedAtIsNull(
            UUID userId, ApprovalStatus approvalStatus, Pageable pageable);
}
