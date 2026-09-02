package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HashtagJpaRepository extends JpaRepository<HashtagJpaRepository, UUID> {
}
