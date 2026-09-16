package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryVectorRepositoryImpl implements CategoryVectorRepository {

    private final CategoryVectorJpaRepository categoryVectorJpaRepository;

    @Override
    public void save(UUID categoryId, float[] vector) {
        categoryVectorJpaRepository.save(CategoryVector.of(categoryId, vector));
    }

    @Override
    public Map<UUID, Double> findSimilarities(float[] hashtagVector, List<UUID> categoryIds) {
        // TODO: pgvector <=> 연산자로 DB에서 직접 코사인 거리 계산하도록 교체
        //  (지금은 뼈대만 - 벡터를 애플리케이션으로 끌고 와 계산하는 임시 구현이라 원래 의도한 최적화가 안 됨)
        throw new UnsupportedOperationException("아직 미구현");
    }
}
