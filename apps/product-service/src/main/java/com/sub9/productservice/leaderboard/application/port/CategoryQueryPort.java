package com.sub9.productservice.leaderboard.application.port;


import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryQueryPort {
    List<CategoryResponse> getCategoriesByIds(List<UUID> ids);
}
