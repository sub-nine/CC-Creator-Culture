package com.sub9.productservice.category.presentation.query.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MergeRequestResponse(
        UUID requestId,
        UUID categoryId,
        String categoryName,
        UUID hashtagId,
        String hashtagName,
        String status,
        LocalDateTime createdAt
) {
}
