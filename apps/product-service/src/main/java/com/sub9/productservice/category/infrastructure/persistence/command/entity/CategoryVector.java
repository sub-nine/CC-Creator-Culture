package com.sub9.productservice.category.infrastructure.persistence.command.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// TODO: embedding 컬럼을 pgvector 타입(vector(N))으로 매핑 - 지금은 뼈대만, float[] <-> vector 변환 미구현
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "p_categories_vector")
public class CategoryVector {

    @Id
    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "embedding", nullable = false)
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
