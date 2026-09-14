package com.sub9.productservice.wishlist.infrastructure.persistence.command;

import com.sub9.productservice.wishlist.domain.model.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface WishlistJpaRepository extends JpaRepository<Wishlist, UUID> {

  @Modifying
  @Query(
  value = """
  INSERT INTO p_wishlists (id, user_id, product_id, created_at)
  VALUES (:id, :userId, :productId, :createdAt)
  ON CONFLICT (user_id, product_id) DO NOTHING
  """, nativeQuery = true)
  void insertIfAbsent(UUID id, UUID userId, UUID productId, Instant createdAt);

  Wishlist findByUserIdAndProductId(UUID userId, UUID productId);

  void deleteAllByProductId(UUID productId);

  int deleteAllByUserIdAndIdIn(UUID userId, Set<UUID> wishlistIds);
}
