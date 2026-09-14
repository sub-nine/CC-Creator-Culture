package com.sub9.productservice.wishlist.application.port.in;

import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface WishlistQueryUseCase {
  Slice<WishlistInfo> getWishlist(UUID userId, Pageable pageable);
}
