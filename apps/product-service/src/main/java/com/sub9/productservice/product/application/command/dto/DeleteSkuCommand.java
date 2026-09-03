package com.sub9.productservice.product.application.command.dto;

import java.util.UUID;

public record DeleteSkuCommand(UUID creatorId, UUID productId, UUID skuId) {}
