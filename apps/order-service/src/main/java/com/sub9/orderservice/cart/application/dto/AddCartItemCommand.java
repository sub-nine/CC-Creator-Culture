package com.sub9.orderservice.cart.application.dto;

import java.util.UUID;

public record AddCartItemCommand(UUID userId, UUID skuId, int quantity) {}
