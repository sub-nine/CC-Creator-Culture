package com.sub9.productservice.product.application.command.dto.sku;

import java.util.UUID;

public record AddSkuCommand(
    UUID productId, UUID creatorId, String name, long price, boolean isDefault, int quantity) {}
