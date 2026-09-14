package com.sub9.productservice.wishlist.infrastructure.persistence.command;

import com.sub9.productservice.wishlist.domain.model.Wishlist;
import com.sub9.productservice.wishlist.domain.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WishlistRepositoryImpl implements WishlistRepository {
  private final WishlistJpaRepository jpaRepository;

  @Override
  public void insertIfAbsent(Wishlist wishlist) {
    jpaRepository.insertIfAbsent(
        wishlist.getId(), wishlist.getUserId(), wishlist.getProductId(), wishlist.getCreatedAt());
  }

  @Override
  public void deleteAllByProductId(UUID productId) {
    jpaRepository.deleteAllByProductId(productId);
  }

  @Override
  public boolean deleteAllByUserIdAndWishlistIds(UUID userId, Set<UUID> wishlistIds) {
    return jpaRepository.deleteAllByUserIdAndIdIn(userId, wishlistIds) > 0;
  }
}
