package com.sub9.productservice.category.presentation.query.dto;

import java.util.List;
import java.util.UUID;

public record CategoryDetailResponse(
        UUID id,
        String name,
        String description,
        List<HashtagResponse> hashtags
) {
}
