package com.sub9.productservice.wishlist.application.port.out;

import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface WishlistProductPort {
  boolean existsByProductId(UUID productId);

  Map<UUID, WishlistInfo> findAllByProductIds(Set<UUID> productIds);
}
