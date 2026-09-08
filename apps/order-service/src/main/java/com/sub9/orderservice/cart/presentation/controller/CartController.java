package com.sub9.orderservice.cart.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.cart.application.service.CartCommandService;
import com.sub9.orderservice.cart.application.service.CartQueryService;
import com.sub9.orderservice.cart.presentation.request.AddCartItemRequest;
import com.sub9.orderservice.cart.presentation.request.DeleteCartItemRequest;
import com.sub9.orderservice.cart.presentation.request.UpdateCartItemRequest;
import com.sub9.orderservice.cart.presentation.response.CartItemResponse;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/cart/items")
public class CartController {
  private final CartCommandService cartCommandService;
  private final CartQueryService cartQueryService;

  @PostMapping
  public ApiResponse<Void> addCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody AddCartItemRequest request) {
    cartCommandService.addCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("장바구니 등록 성공", null);
  }

  @GetMapping
  public ApiResponse<List<CartItemResponse>> getCartItems(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
    return ApiResponse.success("장바구니 조회 성공", cartQueryService.getCart(principal.userId()));
  }

  @PatchMapping("/{cartId}")
  public ApiResponse<Void> updateCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @PathVariable("cartId") UUID cartId,
      @Valid @RequestBody UpdateCartItemRequest request) {
    cartCommandService.updateCartItem(request.toCommand(principal.userId(), cartId));
    return ApiResponse.success("상품 수량이 변경되었습니다.", null);
  }

  @PostMapping("/delete")
  public ApiResponse<Void> removeCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody DeleteCartItemRequest request) {
    cartCommandService.removeCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("상품이 장바구니에서 삭제되었습니다", null);
  }
}
