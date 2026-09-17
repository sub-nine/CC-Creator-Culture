package com.sub9.productservice.category.application.command.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CategoryVectorRepository {

    void save(UUID categoryId, float[] vector);

    // hashtagId 기준으로 p_hashtags_vector와 DB에서 직접 조인해 계산 - 벡터 값을 애플리케이션으로 끌고 오지 않음
    // categoryIds 중 벡터가 아직 없는 카테고리는 결과 Map에서 빠짐 - 호출부에서 누락된 id를 Failed로 처리해야 함
    Map<UUID, Double> findSimilarities(UUID hashtagId, List<UUID> categoryIds);
}
