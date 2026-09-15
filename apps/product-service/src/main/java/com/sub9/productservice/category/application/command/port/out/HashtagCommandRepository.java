package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.application.command.model.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;

import java.util.Optional;
import java.util.UUID;

public interface HashtagCommandRepository {

    Hashtag save(Hashtag hashtag);

    Optional<Hashtag> findById(UUID hashtagId);

    HashtagUpsertResult findOrCreateByName(String name);

    // 원자적 UPDATE로 처리되어 동시 증가에도 충돌이 발생하지 않는다
    void increaseUsageCount(UUID hashtagId);
}
