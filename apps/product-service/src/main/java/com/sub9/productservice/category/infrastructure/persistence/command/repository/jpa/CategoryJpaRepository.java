package com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByIdAndDeletedAtIsNull(UUID id);

    List<Category> findAllByStatusAndDeletedAtIsNull(CategoryStatus status);
}
