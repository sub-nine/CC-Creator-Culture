package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryVectorJpaRepository extends JpaRepository<CategoryVector, UUID> {

    // TODO: pgvector 코사인 거리 연산자(<=>)로 categoryIds 후보 중 유사도 계산하는 네이티브 쿼리 구현
    List<CategoryVector> findByCategoryIdIn(List<UUID> categoryIds);
}
