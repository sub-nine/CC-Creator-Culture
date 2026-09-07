package com.sub9.orderservice.cart.application.dto;

import java.util.UUID;

public record UpdateCartItemCommand(UUID userId, UUID cartId, int quantity) {}
