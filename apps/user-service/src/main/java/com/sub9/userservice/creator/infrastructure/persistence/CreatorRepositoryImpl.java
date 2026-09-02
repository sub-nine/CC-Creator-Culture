package com.sub9.userservice.creator.infrastructure.persistence;

import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
        return creatorJpaRepository.findByCreatorIdAndDeletedAtIsNull(creatorId);
    }

    @Override
    public Optional<Creator> findActiveByUserId(UUID userId) {
        return creatorJpaRepository.findByUserIdAndDeletedAtIsNull(userId);
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
