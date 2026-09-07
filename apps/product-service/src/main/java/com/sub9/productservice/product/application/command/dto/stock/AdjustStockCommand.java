package com.sub9.productservice.product.application.command.dto.stock;

import java.util.UUID;

public record AdjustStockCommand(UUID creatorId, UUID skuId, int quantity) {}
