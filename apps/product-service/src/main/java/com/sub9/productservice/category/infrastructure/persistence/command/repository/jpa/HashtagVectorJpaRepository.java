package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HashtagVectorJpaRepository extends JpaRepository<HashtagVector, UUID> {
}
