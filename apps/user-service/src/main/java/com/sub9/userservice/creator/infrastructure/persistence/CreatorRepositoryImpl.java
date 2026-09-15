package com.sub9.userservice.creator.infrastructure.persistence;

import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
@RequiredArgsConstructor
public class CreatorRepositoryImpl implements CreatorRepository {

    private final CreatorJpaRepository creatorJpaRepository;

    @Override
    public Creator save(Creator creator) {
        return creatorJpaRepository.save(creator);
    }

    @Override
    public void flush() {
        creatorJpaRepository.flush();
    }

    @Override
    public Optional<Creator> findActiveById(UUID creatorId) {
        return creatorJpaRepository.findByIdAndDeletedAtIsNull(creatorId);
    }

    @Override
    public Optional<Creator> findApprovedActiveById(UUID creatorId) {
        return creatorJpaRepository.findApprovedActiveById(
                creatorId, ApprovalStatus.APPROVED);
    }

    @Override
    public Page<Creator> findApprovedActiveByCreatorName(String keyword, Pageable pageable) {
        return creatorJpaRepository.findApprovedActiveByCreatorName(
                ApprovalStatus.APPROVED, keyword, pageable);
    }

    @Override
    public Optional<Creator> findApprovedActiveByIdForUpdate(UUID creatorId) {
        return creatorJpaRepository.findApprovedActiveByIdForUpdate(
                creatorId, ApprovalStatus.APPROVED);
    }

    @Override
    public Optional<Creator> findActiveByIdForUpdate(UUID creatorId) {
        return creatorJpaRepository.findActiveByIdForUpdate(creatorId);
    }

    @Override
    public Optional<Creator> findActiveByUserId(UUID userId) {
        return creatorJpaRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public Optional<Creator> findApprovedActiveByUserId(UUID userId) {
        return creatorJpaRepository.findApprovedActiveByUserId(
                userId, ApprovalStatus.APPROVED);
    }

    @Override
    public Optional<Creator> findApprovedActiveByUserIdForUpdate(UUID userId) {
        return creatorJpaRepository.findApprovedActiveByUserIdForUpdate(
                userId, ApprovalStatus.APPROVED);
    }

    @Override
    public Optional<Creator> findActiveByUserIdForUpdate(UUID userId) {
        return creatorJpaRepository.findActiveByUserIdForUpdate(userId);
    }

    @Override
    public boolean existsByCreatorNameIncludingDeleted(String creatorName) {
        return creatorJpaRepository.existsByCreatorName(creatorName);
    }

    @Override
    public boolean existsByBusinessRegistrationNumberIncludingDeleted(
            String businessRegistrationNumber) {
        return creatorJpaRepository.existsByBusinessRegistrationNumber(businessRegistrationNumber);
    }
}
