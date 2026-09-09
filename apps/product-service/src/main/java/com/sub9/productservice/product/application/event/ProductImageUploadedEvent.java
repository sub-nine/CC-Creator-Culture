package com.sub9.productservice.product.application.event;

import java.util.UUID;

public record ProductImageUploadedEvent(UUID eventId, UUID imageId, UUID productId, String originalKey) {}
