package com.sub9.userservice.creator.infrastructure.persistence;

import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Creator> findByIdAndApprovalStatusAndDeletedAtIsNull(
            UUID id, ApprovalStatus approvalStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select creator from Creator creator "
            + "where creator.id = :creatorId and creator.deletedAt is null")
    Optional<Creator> findActiveByIdForUpdate(@Param("creatorId") UUID creatorId);

    Optional<Creator> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByCreatorName(String creatorName);

    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
}
