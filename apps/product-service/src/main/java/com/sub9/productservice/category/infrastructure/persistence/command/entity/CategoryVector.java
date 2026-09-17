package com.sub9.productservice.category.infrastructure.persistence.command.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// TODO: 벡터 차원(768)은 jhgan/ko-sroberta-multitask 기준 - 실제 채택 모델 확정되면 재확인
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_categories_vector")
public class CategoryVector {

    @Id
    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(768)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CategoryVector of(UUID categoryId, float[] embedding) {
        CategoryVector vector = new CategoryVector();
        vector.categoryId = categoryId;
        vector.embedding = embedding;
        vector.createdAt = Instant.now();
        return vector;
    }
}
