package com.sub9.productservice.product.application.event;

import java.util.UUID;

public record ProductViewedEvent(UUID productId, UUID viewerId) {}
