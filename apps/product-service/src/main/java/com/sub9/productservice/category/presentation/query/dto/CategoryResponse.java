package com.sub9.productservice.category.presentation.query.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description
) {
}
