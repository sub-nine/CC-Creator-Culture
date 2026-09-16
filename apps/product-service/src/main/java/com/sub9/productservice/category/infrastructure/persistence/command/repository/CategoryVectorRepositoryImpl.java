package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    // 별도 트랜잭션(REQUIRES_NEW)으로 실행 - 이 조회가 실패해도 tryLink()의 메인 트랜잭션(다른 후보 처리)을
    // 오염시키지 않게 분리함 (Postgres는 트랜잭션 내 쿼리 하나가 에러나면 그 트랜잭션 전체를 abort시킴)
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<UUID, Double> findSimilarities(UUID hashtagId, List<UUID> categoryIds) {
        // cosine_distance는 [0, 2] 범위(1 - 코사인유사도) - 기존 threshold(코사인 유사도 기준)와 맞추려고 변환
        return categoryVectorJpaRepository.findDistancesByHashtagId(hashtagId, categoryIds).stream()
                .collect(Collectors.toMap(
                        CategoryVectorJpaRepository.CategoryDistance::getCategoryId,
                        distance -> 1.0 - distance.getDistance()
                ));
    }
}
