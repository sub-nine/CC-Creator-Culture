package com.sub9.productservice.product.application.command.dto.product;

import java.util.UUID;

public record CreatePresignedUrlCommand(UUID creatorId, String contentType, long fileSize) {}
