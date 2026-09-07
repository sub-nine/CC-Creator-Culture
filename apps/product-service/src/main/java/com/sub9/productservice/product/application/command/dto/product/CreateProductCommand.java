package com.sub9.productservice.product.application.command.dto.product;

import com.sub9.productservice.product.application.command.dto.sku.CreateSkuCommand;

import java.util.List;
import java.util.UUID;

public record CreateProductCommand(
        List<String> hashTags,
        UUID creatorId,
        String name,
        String content,
        List<CreateSkuCommand> skus
) {
}
