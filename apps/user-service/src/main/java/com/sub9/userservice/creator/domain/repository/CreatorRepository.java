package com.sub9.userservice.creator.domain.repository;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CreatorRepository {

    Creator save(Creator creator);

    void flush();

    Optional<Creator> findActiveById(UUID creatorId);

    Optional<Creator> findApprovedActiveById(UUID creatorId);

    Page<Creator> findApprovedActive(Pageable pageable);

    Page<Creator> findApprovedActiveByCreatorName(String keyword, Pageable pageable);

    Optional<Creator> findApprovedActiveByIdForUpdate(UUID creatorId);

    Optional<Creator> findActiveByIdForUpdate(UUID creatorId);

    Optional<Creator> findActiveByUserId(UUID userId);

    Optional<Creator> findApprovedActiveByUserId(UUID userId);

    Optional<Creator> findApprovedActiveByUserIdForUpdate(UUID userId);

    List<Creator> findApprovedActiveByUserIds(List<UUID> userIds);
  
    Optional<Creator> findActiveByUserIdForUpdate(UUID userId);

    // 삭제된 창작자 정보의 고유 값도 재사용할 수 없으므로 전체 행을 검사한다.
    boolean existsByCreatorNameIncludingDeleted(String creatorName);

    boolean existsByBusinessRegistrationNumberIncludingDeleted(String businessRegistrationNumber);
}
