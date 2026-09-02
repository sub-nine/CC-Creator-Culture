package com.sub9.productservice.product.application.command.dto;

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
