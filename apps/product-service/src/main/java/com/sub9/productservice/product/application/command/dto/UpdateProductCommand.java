package com.sub9.productservice.product.application.command.dto;

import java.util.UUID;

public record UpdateProductCommand(UUID creatorId, UUID productId, String name, String content) {}
