package com.sub9.userservice.creator.domain.repository;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.Optional;
import java.util.UUID;

public interface CreatorRepository {

    Creator save(Creator creator);

    void flush();

    Optional<Creator> findActiveById(UUID creatorId);

    Optional<Creator> findActiveByUserId(UUID userId);

    // 삭제된 창작자 정보의 고유 값도 재사용할 수 없으므로 전체 행을 검사한다.
    boolean existsByCreatorNameIncludingDeleted(String creatorName);

    boolean existsByBusinessRegistrationNumberIncludingDeleted(String businessRegistrationNumber);
}
