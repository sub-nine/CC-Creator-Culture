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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByIdAndDeletedAtIsNull(UUID id);

    @Query("select creator from Creator creator join creator.user user "
            + "where creator.id = :creatorId "
            + "and creator.approvalStatus = :approvalStatus "
            + "and creator.deletedAt is null "
            + "and user.deletedAt is null")
    Optional<Creator> findApprovedActiveById(
            @Param("creatorId") UUID creatorId,
            @Param("approvalStatus") ApprovalStatus approvalStatus);

    @Query(
            value = "select creator from Creator creator join creator.user user "
                    + "where creator.approvalStatus = :approvalStatus "
                    + "and creator.deletedAt is null "
                    + "and user.deletedAt is null "
                    + "and (:keyword is null "
                    + "or lower(creator.creatorName) like lower(concat('%', :keyword, '%')))",
            countQuery = "select count(creator) from Creator creator join creator.user user "
                    + "where creator.approvalStatus = :approvalStatus "
                    + "and creator.deletedAt is null "
                    + "and user.deletedAt is null "
                    + "and (:keyword is null "
                    + "or lower(creator.creatorName) like lower(concat('%', :keyword, '%')))")
    Page<Creator> findApprovedActiveByCreatorName(
            @Param("approvalStatus") ApprovalStatus approvalStatus,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select creator from Creator creator "
            + "where creator.id = :creatorId "
            + "and creator.approvalStatus = :approvalStatus "
            + "and creator.deletedAt is null")
    Optional<Creator> findApprovedActiveByIdForUpdate(
            @Param("creatorId") UUID creatorId,
            @Param("approvalStatus") ApprovalStatus approvalStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select creator from Creator creator "
            + "where creator.id = :creatorId and creator.deletedAt is null")
    Optional<Creator> findActiveByIdForUpdate(@Param("creatorId") UUID creatorId);

    Optional<Creator> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("select creator from Creator creator join creator.user user "
            + "where creator.userId = :userId "
            + "and creator.approvalStatus = :approvalStatus "
            + "and creator.deletedAt is null "
            + "and user.deletedAt is null")
    Optional<Creator> findApprovedActiveByUserId(
            @Param("userId") UUID userId,
            @Param("approvalStatus") ApprovalStatus approvalStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select creator from Creator creator "
            + "where creator.userId = :userId and creator.deletedAt is null")
    Optional<Creator> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    boolean existsByCreatorName(String creatorName);

    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
}
