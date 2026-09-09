package com.sub9.productservice.leaderboard.application.port.out;


import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryQueryPort {
    List<CategoryResponse> getCategoriesByIds(List<UUID> ids);

    List<ProductCategoryIdsResponse> getCategoryIdsByProductIds(List<UUID> productIds);
}
