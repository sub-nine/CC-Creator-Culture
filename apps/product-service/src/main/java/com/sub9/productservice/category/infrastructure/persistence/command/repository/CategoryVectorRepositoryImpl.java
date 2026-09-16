package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CategoryVectorRepositoryImpl implements CategoryVectorRepository {

    private final CategoryVectorJpaRepository categoryVectorJpaRepository;

    @Override
    public void save(UUID categoryId, float[] vector) {
        categoryVectorJpaRepository.save(CategoryVector.of(categoryId, vector));
    }

    @Override
    public Map<UUID, Double> findSimilarities(UUID hashtagId, List<UUID> categoryIds) {
        // cosine_distance는 [0, 2] 범위(1 - 코사인유사도) - 기존 threshold(코사인 유사도 기준)와 맞추려고 변환
        return categoryVectorJpaRepository.findDistancesByHashtagId(hashtagId, categoryIds).stream()
                .collect(Collectors.toMap(
                        CategoryVectorJpaRepository.CategoryDistance::getCategoryId,
                        distance -> 1.0 - distance.getDistance()
                ));
    }
}
