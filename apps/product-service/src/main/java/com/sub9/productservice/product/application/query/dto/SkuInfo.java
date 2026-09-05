package com.sub9.productservice.product.application.query.dto;

import com.sub9.productservice.product.domain.model.ProductStatus;
import java.util.UUID;

public record SkuInfo(
    UUID skuId,
    UUID productId,
    UUID creatorId,
    String productName,
    String skuName,
    ProductStatus productStatus,
    Long price,
    int quantity) {}
