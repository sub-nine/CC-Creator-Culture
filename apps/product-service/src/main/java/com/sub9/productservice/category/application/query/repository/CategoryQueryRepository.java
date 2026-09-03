package com.sub9.productservice.category.application.query.repository;

import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CategoryQueryRepository {

    Page<CategoryResponse> searchCategories(String keyword, Pageable pageable);

    Optional<CategoryResponse> findById(UUID categoryId);
}
