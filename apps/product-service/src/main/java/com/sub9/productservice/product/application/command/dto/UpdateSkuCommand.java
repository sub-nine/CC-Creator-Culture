package com.sub9.productservice.product.application.command.dto;

import java.util.UUID;

public record UpdateSkuCommand(
    UUID creatorId, UUID productId, UUID skuId, String name, Long price, boolean isDefault) {}
