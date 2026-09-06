package com.sub9.orderservice.cart.application.dto;

import java.util.UUID;

public record CartItemInfo(
    UUID cartId,
    UUID skuId,
    UUID productId,
    UUID creatorId,
    String productName,
    String skuName,
    String productStatus,
    Long price,
    int quantity) {}
