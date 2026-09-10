package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.HashtagProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HashtagProductJpaRepository extends JpaRepository<HashtagProduct, UUID> {

    // 이미 연결돼 있으면 아무것도 반환하지 않고, 실제로 새로 연결된 경우에만 id를 반환(레이스 컨디션 방지)
    // @Modifying을 붙이면 executeUpdate()로 실행되어 RETURNING 결과(Optional<UUID>)를 받을 수 없으므로 붙이지 않는다
    @Query(value = """
            INSERT INTO p_hashtags_products (id, hashtag_id, product_id, unique_version, created_at)
            VALUES (:id, :hashtagId, :productId, :uniqueVersion, now())
            ON CONFLICT (hashtag_id, product_id, unique_version) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<UUID> insertLinkIfAbsent(
            @Param("id") UUID id,
            @Param("hashtagId") UUID hashtagId,
            @Param("productId") UUID productId,
            @Param("uniqueVersion") UUID uniqueVersion);
}
