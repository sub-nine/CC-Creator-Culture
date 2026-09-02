package com.sub9.userservice.creator.infrastructure.persistence;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorJpaRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Creator> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByCreatorName(String creatorName);

    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
}
