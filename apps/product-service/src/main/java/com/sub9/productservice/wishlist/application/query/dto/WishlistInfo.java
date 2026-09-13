package com.sub9.productservice.wishlist.application.query.dto;

import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.UUID;

public record WishlistInfo(
    UUID wishlistId,
    UUID productId,
    String productName,
    String status,
    Long price,
    String imageUrl) {
  public static WishlistInfo of(Wishlist wishlist, WishlistInfo info) {
    return new WishlistInfo(
        wishlist.getId(),
        wishlist.getProductId(),
        info.productName(),
        info.status,
        info.price,
        info.imageUrl);
  }
}
