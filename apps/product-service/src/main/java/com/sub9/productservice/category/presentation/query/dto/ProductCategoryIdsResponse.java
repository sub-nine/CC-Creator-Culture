package com.sub9.productservice.category.presentation.query.dto;

import java.util.List;
import java.util.UUID;

public record ProductCategoryIdsResponse(
        UUID productId,
        List<UUID> categoryIds
) {
}
