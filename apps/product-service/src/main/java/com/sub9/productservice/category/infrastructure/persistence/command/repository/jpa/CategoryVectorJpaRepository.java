package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoryVectorJpaRepository extends JpaRepository<CategoryVector, UUID> {

    // p_hashtags_vector와 p_categories_vector를 DB에서 직접 조인 - 해시태그 벡터 값이 애플리케이션으로 안 올라옴
    // cosine_distance()는 Hibernate가 pgvector의 <=> 연산자로 변환해줌 (Hibernate 7 / hibernate-vector)
    @Query("SELECT cv.categoryId AS categoryId, cosine_distance(cv.embedding, hv.embedding) AS distance "
            + "FROM CategoryVector cv, HashtagVector hv "
            + "WHERE hv.hashtagId = :hashtagId AND cv.categoryId IN :categoryIds")
    List<CategoryDistance> findDistancesByHashtagId(
            @Param("hashtagId") UUID hashtagId, @Param("categoryIds") List<UUID> categoryIds);

    interface CategoryDistance {
        UUID getCategoryId();

        double getDistance();
    }
}
