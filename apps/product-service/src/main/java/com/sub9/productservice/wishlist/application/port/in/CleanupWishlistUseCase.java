package com.sub9.productservice.wishlist.application.port.in;

import java.util.UUID;

public interface CleanupWishlistUseCase {
  void cleanUpWByProductId(UUID productId);
}
