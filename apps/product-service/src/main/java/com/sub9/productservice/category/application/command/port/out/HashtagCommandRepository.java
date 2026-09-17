package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.application.command.model.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HashtagCommandRepository {

    Hashtag save(Hashtag hashtag);

    Optional<Hashtag> findById(UUID hashtagId);

    HashtagUpsertResult findOrCreateByName(String name);

    // 원자적 UPDATE로 처리되어 동시 증가에도 충돌이 발생하지 않는다
    void increaseUsageCount(UUID hashtagId);

    // 카테고리 검사 실패로 어떤 카테고리와도 연결되지 못한 채 보류된 해시태그 id 목록 - 최대 limit개
    List<UUID> findIdsWithoutCategoryLink(int limit);
}
