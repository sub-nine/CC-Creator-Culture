package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.domain.entity.Hashtag;

import java.util.Optional;
import java.util.UUID;

public interface HashtagCommandRepository {

    Hashtag save(Hashtag hashtag);

    Optional<Hashtag> findById(UUID hashtagId);

    HashtagUpsertResult findOrCreateByName(String name);

    // 동시 증가로 인한 낙관적 락 충돌 발생 시 내부적으로 재시도
    Hashtag increaseUsageCount(UUID hashtagId);
}
