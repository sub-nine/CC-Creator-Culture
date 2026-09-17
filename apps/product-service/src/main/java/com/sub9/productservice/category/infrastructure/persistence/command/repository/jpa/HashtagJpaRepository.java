package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.Hashtag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HashtagJpaRepository extends JpaRepository<Hashtag, UUID> {
    Optional<Hashtag> findByIdAndDeletedAtIsNull(UUID hashtagId);

    Optional<Hashtag> findByNameAndDeletedAtIsNull(String name);

    // 카테고리 검사 실패(예: 임베딩 서버 타임아웃)로 어떤 카테고리와도 연결되지 못한 채 보류된 해시태그 - 스케줄러가 재시도 대상으로 사용
    @Query("SELECT h.id FROM Hashtag h WHERE h.deletedAt IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM CategoryHashtag ch WHERE ch.hashtag.id = h.id AND ch.deletedAt IS NULL)")
    List<UUID> findIdsWithoutCategoryLink(Pageable pageable);

    // 원자적 증가로 동시성 충돌 자체를 차단(낙관적 락+재시도 대신 채택). version도 같이 올려 stale 엔티티의 덮어쓰기를 방지
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Hashtag h SET h.usageCount = h.usageCount + 1, h.version = h.version + 1 " +
            "WHERE h.id = :hashtagId AND h.deletedAt IS NULL")
    int increaseUsageCount(@Param("hashtagId") UUID hashtagId);

    // 동시에 같은 이름으로 생성 요청이 들어와도 유니크 제약 위반 없이 하나만 삽입되도록 처리(레이스 컨디션 방지)
    // 실제로 이번 호출로 새로 삽입된 경우에만 id를 반환(이미 존재해서 스킵된 경우 빈 값)
    @Query(value = """
            INSERT INTO p_hashtags (id, name, usage_count, unique_version, version, created_at)
            VALUES (:id, :name, 0, :uniqueVersion, 0, now())
            ON CONFLICT (name, unique_version) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<UUID> insertIfAbsent(@Param("id") UUID id, @Param("name") String name, @Param("uniqueVersion") UUID uniqueVersion);
}
