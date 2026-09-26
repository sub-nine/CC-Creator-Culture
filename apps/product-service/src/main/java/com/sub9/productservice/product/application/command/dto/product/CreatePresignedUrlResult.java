package com.sub9.productservice.product.application.command.dto.product;

import java.util.UUID;

public record CreatePresignedUrlResult(UUID uploadId, String uploadUrl) {}
