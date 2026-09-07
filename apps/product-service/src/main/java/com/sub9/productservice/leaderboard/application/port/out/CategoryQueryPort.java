package com.sub9.productservice.leaderboard.application.port.out;


import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CategoryQueryPort {
    List<CategoryResponse> getCategoriesByIds(List<UUID> ids);

    Map<UUID, UUID> getCategoryIdsByProductIds(List<UUID> productIds);
}
