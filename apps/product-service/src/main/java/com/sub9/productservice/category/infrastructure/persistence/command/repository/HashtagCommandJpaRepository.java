package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.domain.entity.Hashtag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HashtagCommandJpaRepository extends JpaRepository<Hashtag, UUID> {
    Optional<Hashtag> findByIdAndDeletedAtIsNull(UUID hashtagId);
}
