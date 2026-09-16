package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Category> findByNameAndDeletedAtIsNull(String name);

    List<Category> findAllByStatusAndDeletedAtIsNull(CategoryStatus status);

    // 동시에 같은 이름으로 생성 요청이 들어와도 유니크 제약 위반 없이 하나만 삽입되도록 처리(레이스 컨디션 방지)
    // 실제로 이번 호출로 새로 삽입된 경우에만 id를 반환(이미 존재해서 스킵된 경우 빈 값)
    @Query(value = """
            INSERT INTO p_categories (id, name, description, status, unique_version, created_at)
            VALUES (:id, :name, :description, :status, :uniqueVersion, now())
            ON CONFLICT (name, unique_version) DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<UUID> insertIfAbsent(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("status") String status,
            @Param("uniqueVersion") UUID uniqueVersion
    );
}
