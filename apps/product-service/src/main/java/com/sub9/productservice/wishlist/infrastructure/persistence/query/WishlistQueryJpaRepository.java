package com.sub9.productservice.wishlist.infrastructure.persistence.query;

import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.Repository;

public interface WishlistQueryJpaRepository extends Repository<Wishlist, UUID> {
  Slice<Wishlist> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
}
