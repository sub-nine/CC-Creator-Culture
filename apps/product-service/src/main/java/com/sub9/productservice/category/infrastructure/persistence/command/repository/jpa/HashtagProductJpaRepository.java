package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.HashtagProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HashtagProductJpaRepository extends JpaRepository<HashtagProduct, UUID> {
}
