package com.sub9.productservice.wishlist.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;
import com.sub9.productservice.wishlist.application.port.in.CleanupWishlistUseCase;
import com.sub9.productservice.wishlist.application.port.in.WishlistCommandUseCase;
import com.sub9.productservice.wishlist.application.port.out.WishlistProductPort;
import com.sub9.productservice.wishlist.domain.exception.WishlistErrorCode;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import com.sub9.productservice.wishlist.domain.repository.WishlistRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WishlistCommandService implements WishlistCommandUseCase, CleanupWishlistUseCase {
  private final WishlistProductPort wishlistProductPort;
  private final WishlistRepository wishlistRepository;

  @Override
  public void addToWishlist(AddToWishlistCommand command) {
    existsByProductId(command.productId());

    Wishlist wishlist = Wishlist.create(command.userId(), command.productId());
    wishlistRepository.insertIfAbsent(wishlist);
  }

  @Override
  public void removeFromWishlist(RemoveFromWishlistCommand command) {
    if (!wishlistRepository.deleteAllByUserIdAndWishlistIds(
        command.userId(), command.wishlistIds())) {
      throw new BusinessException(WishlistErrorCode.WISHLIST_NOT_FOUND);
    }
  }

  @Override
  public void cleanUpWByProductId(UUID productId) {
    wishlistRepository.deleteAllByProductId(productId);
  }

  // ============================== Helper Method ====================================
  private void existsByProductId(UUID productId) {
    if (!wishlistProductPort.existsByProductId(productId)) {
      throw new BusinessException(WishlistErrorCode.PRODUCT_NOT_AVAILABLE);
    }
  }
}
