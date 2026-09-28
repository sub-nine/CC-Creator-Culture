package com.sub9.productservice.wishlist.presentation.query.controller;

import com.sub9.common.annotation.Customer;
import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.dto.response.PageResponse;
import com.sub9.productservice.common.config.s3.S3Properties;
import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.wishlist.application.port.in.WishlistQueryUseCase;
import com.sub9.productservice.wishlist.presentation.query.dto.WishlistResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Customer
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wishlist")
public class WishlistQueryController {
  private final WishlistQueryUseCase wishlistQueryUseCase;
  private final S3Properties s3Properties;

  @GetMapping
  public ApiResponse<PageResponse<WishlistResponse>> getWishlist(
      @AuthenticationPrincipal AuthUser authUser,
      @RequestParam(defaultValue = "0") @Min(0) int pageNum) {
    Pageable pageable = PageRequest.of(pageNum, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

    Slice<WishlistResponse> response =
        wishlistQueryUseCase
            .getWishlist(authUser.id(), pageable)
            .map(info -> WishlistResponse.from(info, s3Properties.publicUrl()));

    return ApiResponse.success("관심상품 목록 조회 성공", PageResponse.of(response));
  }
}
