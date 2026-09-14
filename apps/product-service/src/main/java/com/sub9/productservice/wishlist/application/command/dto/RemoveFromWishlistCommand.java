package com.sub9.productservice.wishlist.application.command.dto;

import java.util.Set;
import java.util.UUID;

public record RemoveFromWishlistCommand(UUID userId, Set<UUID> wishlistIds) {}
