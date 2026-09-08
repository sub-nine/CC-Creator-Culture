package com.sub9.productservice.leaderboard.application.model;

import java.util.UUID;

public record ProductQuantity(
        UUID productId,
        Long quantity
) {
}
