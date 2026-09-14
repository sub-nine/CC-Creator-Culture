package com.sub9.productservice.wishlist.domain.repository;

import com.sub9.productservice.wishlist.domain.model.Wishlist;

import java.util.Set;
import java.util.UUID;

public interface WishlistRepository {
  void insertIfAbsent(Wishlist wishlist);

  void deleteAllByProductId(UUID productId);

  boolean deleteAllByUserIdAndWishlistIds(UUID userId, Set<UUID> wishlistIds);
}
