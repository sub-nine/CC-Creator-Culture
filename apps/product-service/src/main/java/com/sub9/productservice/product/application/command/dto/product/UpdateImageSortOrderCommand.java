package com.sub9.productservice.product.application.command.dto.product;

import java.util.List;
import java.util.UUID;

public record UpdateImageSortOrderCommand(UUID productId, UUID creatorId, List<UUID> imageIds) {}
