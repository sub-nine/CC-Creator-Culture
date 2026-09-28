package com.sub9.productservice.product.application.command.dto.product;

import java.util.List;
import java.util.UUID;

public record AddImagesCommand(UUID productId, UUID creatorId, List<UUID> imageUploadIds) {}
