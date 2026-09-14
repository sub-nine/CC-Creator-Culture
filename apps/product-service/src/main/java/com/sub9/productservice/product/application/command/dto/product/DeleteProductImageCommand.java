package com.sub9.productservice.product.application.command.dto.product;

import java.util.UUID;

public record DeleteProductImageCommand(UUID creatorId, UUID productId, UUID imageId) {}
