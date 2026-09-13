package com.sub9.productservice.wishlist.presentation.command.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;
import com.sub9.productservice.wishlist.application.port.in.WishlistCommandUseCase;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wishlist")
public class WishlistCommandController {
  private final WishlistCommandUseCase wishlistCommandUseCase;

  @PostMapping("/{productId}")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Void> addToWishlist(
      @AuthenticationPrincipal(expression = "id()") UUID userId,
      @PathVariable UUID productId) {
    wishlistCommandUseCase.addToWishlist(new AddToWishlistCommand(userId, productId));
    return ApiResponse.success("관심상품 등록에 성공했습니다.", null);
  }

  @DeleteMapping
  public ApiResponse<Void> removeFromWishlist(
      @AuthenticationPrincipal(expression = "id()") UUID userId,
      @RequestBody @NotEmpty List<@NotNull UUID> wishlistIds) {
    wishlistCommandUseCase.removeFromWishlist(new RemoveFromWishlistCommand(userId, Set.copyOf(wishlistIds)));
    return ApiResponse.success("관심상품 삭제에 성공했습니다.", null);
  }
}
