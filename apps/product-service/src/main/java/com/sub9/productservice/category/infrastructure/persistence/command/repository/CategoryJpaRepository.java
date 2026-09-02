package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {
}
