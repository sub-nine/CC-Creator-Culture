package com.sub9.productservice.wishlist.application.port.out;

import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface WishlistQueryRepository {
  Slice<Wishlist> findAllByUserId(UUID userId, Pageable pageable);
}
