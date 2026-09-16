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
@Table(name = "p_hashtags_vector")
public class HashtagVector {

    @Id
    @Column(name = "hashtag_id", nullable = false, updatable = false)
    private UUID hashtagId;

    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static HashtagVector of(UUID hashtagId, float[] embedding) {
        HashtagVector vector = new HashtagVector();
        vector.hashtagId = hashtagId;
        vector.embedding = embedding;
        vector.createdAt = Instant.now();
        return vector;
    }
}
