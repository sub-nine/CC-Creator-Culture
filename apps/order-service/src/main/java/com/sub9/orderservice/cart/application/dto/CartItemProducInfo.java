package com.sub9.orderservice.cart.application.dto;

import java.util.UUID;

public record CartItemProducInfo(
    UUID skuId,
    UUID productId,
    UUID creatorId,
    String productName,
    String skuName,
    String productStatus,
    Long price,
    int quantity) {}
