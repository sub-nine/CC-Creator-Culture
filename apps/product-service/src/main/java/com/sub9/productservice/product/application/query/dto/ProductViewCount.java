package com.sub9.productservice.product.application.query.dto;

import java.util.UUID;

public record ProductViewCount(
        UUID productId,
        Long viewCount
) {}
