package com.sub9.productservice.product.presentation.query.dto;

import com.sub9.productservice.product.domain.model.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID productId,
    String name,
    ProductStatus status,
    BigDecimal averageRating,
    Long reviewCount,
    Long price,
    int quantity) {}
