package com.sub9.orderservice.cart.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.orderservice.cart.application.port.in.CartCommandUseCase;
import com.sub9.orderservice.cart.application.port.in.CartQueryUseCase;
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
  private final CartCommandUseCase cartCommandUseCase;
  private final CartQueryUseCase cartQueryUseCase;

  @PostMapping
  public ApiResponse<UUID> addCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody AddCartItemRequest request) {
    UUID cartId = cartCommandUseCase.addCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("장바구니 등록 성공", cartId);
  }

  @GetMapping
  public ApiResponse<List<CartItemResponse>> getCartItems(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
    return ApiResponse.success("장바구니 조회 성공", cartQueryUseCase.getCart(principal.userId()));
  }

  @PatchMapping("/{cartId}")
  public ApiResponse<Void> updateCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @PathVariable("cartId") UUID cartId,
      @Valid @RequestBody UpdateCartItemRequest request) {
    cartCommandUseCase.updateCartItem(request.toCommand(principal.userId(), cartId));
    return ApiResponse.success("상품 수량이 변경되었습니다.", null);
  }

  @PostMapping("/delete")
  public ApiResponse<Void> removeCartItem(
      @AuthenticationPrincipal GatewayAuthenticationPrincipal principal,
      @Valid @RequestBody DeleteCartItemRequest request) {
    cartCommandUseCase.removeCartItem(request.toCommand(principal.userId()));
    return ApiResponse.success("상품이 장바구니에서 삭제되었습니다", null);
  }
}
