package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.CategoryVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoryVectorJpaRepository extends JpaRepository<CategoryVector, UUID> {

    // cosine_distance()는 Hibernate가 pgvector의 <=> 연산자로 변환해줌 (Hibernate 7 / hibernate-vector)
    @Query("SELECT cv.categoryId AS categoryId, cosine_distance(cv.embedding, :vector) AS distance "
            + "FROM CategoryVector cv WHERE cv.categoryId IN :categoryIds")
    List<CategoryDistance> findDistances(@Param("vector") float[] vector, @Param("categoryIds") List<UUID> categoryIds);

    interface CategoryDistance {
        UUID getCategoryId();

        double getDistance();
    }
}
