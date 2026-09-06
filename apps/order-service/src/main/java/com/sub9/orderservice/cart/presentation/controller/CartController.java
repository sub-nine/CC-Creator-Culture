package com.sub9.orderservice.cart.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.cart.application.service.CartService;
import com.sub9.orderservice.cart.presentation.request.AddCartItemRequest;
import com.sub9.orderservice.cart.presentation.request.DeleteCartItemRequest;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/cart/items")
public class CartController {
  private final CartService cartService;

  @PostMapping
  public ApiResponse<Void> addCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody AddCartItemRequest request) {
    cartService.addCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("장바구니 등록 성공", null);
  }

  @PostMapping("/delete")
  public ApiResponse<Void> removeCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody DeleteCartItemRequest request) {
    cartService.removeCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("상품이 장바구니에서 삭제되었습니다", null);
  }
}
