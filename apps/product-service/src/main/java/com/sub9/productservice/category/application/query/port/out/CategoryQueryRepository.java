package com.sub9.productservice.category.application.query.port.out;

import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryQueryRepository {

    Page<CategoryResponse> searchCategories(String keyword, Pageable pageable);

    Optional<CategoryResponse> findById(UUID categoryId);

    List<CategoryResponse> searchCategoriesByIds(List<UUID> ids);

    List<HashtagResponse> findHashtagsByCategoryId(UUID categoryId);

    Page<HashtagResponse> findHashtagsByCategoryId(UUID categoryId, Pageable pageable);

    List<ProductCategoryIdsResponse> findCategoryIdsByProductIds(List<UUID> productIds);
}
