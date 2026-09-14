package com.sub9.productservice.wishlist.presentation.query.dto;

import com.sub9.productservice.common.support.ImageUrlUtils;
import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import java.util.UUID;

public record WishlistResponse(
    UUID wishlistId,
    UUID productId,
    String productName,
    String status,
    Long price,
    String imageUrl) {
  public static WishlistResponse from(WishlistInfo info, String publicUrl) {
    return new WishlistResponse(
        info.wishlistId(),
        info.productId(),
        info.productName(),
        info.status(),
        info.price(),
        ImageUrlUtils.toImageUrl(publicUrl, info.imageUrl()));
  }
}
