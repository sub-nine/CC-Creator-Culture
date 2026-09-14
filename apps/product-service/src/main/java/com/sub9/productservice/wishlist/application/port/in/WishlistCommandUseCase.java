package com.sub9.productservice.wishlist.application.port.in;

import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;

public interface WishlistCommandUseCase {
  void addToWishlist(AddToWishlistCommand command);

  void removeFromWishlist(RemoveFromWishlistCommand command);
}
