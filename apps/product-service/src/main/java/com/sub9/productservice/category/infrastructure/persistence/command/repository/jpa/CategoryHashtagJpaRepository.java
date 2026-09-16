package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryHashtagJpaRepository extends JpaRepository<CategoryHashtag, UUID> {
    Optional<CategoryHashtag> findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(UUID categoryId, UUID hashtagId);

    Optional<CategoryHashtag> findByIdAndDeletedAtIsNull(UUID categoryHashtagId);

    // 동시에 같은 (category, hashtag) 조합으로 연결 요청이 들어와도 유니크 제약 위반 없이 하나만
    // 삽입되도록 처리(레이스 컨디션 방지). 이미 있으면 조용히 스킵(빈 값 반환)
    @Query(value = """
            INSERT INTO p_categories_hashtags
                (id, category_id, hashtag_id, match_type, status, similarity_score, unique_version, created_at)
            VALUES (:id, :categoryId, :hashtagId, :matchType, :status, :similarityScore, :uniqueVersion, now())
            ON CONFLICT (category_id, hashtag_id, unique_version) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<UUID> insertIfAbsent(
            @Param("id") UUID id,
            @Param("categoryId") UUID categoryId,
            @Param("hashtagId") UUID hashtagId,
            @Param("matchType") String matchType,
            @Param("status") String status,
            @Param("similarityScore") double similarityScore,
            @Param("uniqueVersion") UUID uniqueVersion
    );
}
