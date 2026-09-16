package com.sub9.productservice.category.application.command.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CategoryVectorRepository {

    void save(UUID categoryId, float[] vector);

    // categoryIds 중 벡터가 아직 없는 카테고리는 결과 Map에서 빠짐 - 호출부에서 누락된 id를 Failed로 처리해야 함
    Map<UUID, Double> findSimilarities(float[] hashtagVector, List<UUID> categoryIds);
}
