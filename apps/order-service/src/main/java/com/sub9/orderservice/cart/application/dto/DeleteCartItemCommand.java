package com.sub9.orderservice.cart.application.dto;

import java.util.List;
import java.util.UUID;

public record DeleteCartItemCommand(UUID userId, List<UUID> cartIds) {}
