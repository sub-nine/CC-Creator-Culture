package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryHashtagJpaRepository extends JpaRepository<CategoryHashtag, UUID> {
    Optional<CategoryHashtag> findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(UUID categoryId, UUID hashtagId);

    Optional<CategoryHashtag> findByIdAndDeletedAtIsNull(UUID categoryHashtagId);
}
