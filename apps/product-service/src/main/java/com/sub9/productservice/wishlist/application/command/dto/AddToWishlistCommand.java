package com.sub9.productservice.wishlist.application.command.dto;

import java.util.UUID;

public record AddToWishlistCommand(UUID userId, UUID productId) {}
